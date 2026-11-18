package fr.ledcom.signaire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The whole stack, as users run it: the command line extracts signs from a (fake) SignSuisse into bundles, and the
 * web application, a separate process with Elasticsearch and PostgreSQL, loads them; the tests are users of the
 * web application, through HTTP.
 */
class FullStackIT {

    /** Keep in line with compose.yaml. */
    private static final String ELASTICSEARCH = "docker.elastic.co/elasticsearch/elasticsearch:9.4.8";
    private static final String POSTGRES = "postgres:18-alpine";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    private static Path work;
    private static FakeSignSuisse signSuisse;
    private static ElasticsearchContainer elasticsearch;
    private static PostgreSQLContainer postgres;
    private static JavaProcess web;
    private static URI base;

    @BeforeAll
    static void startTheStack() throws Exception {
        work = Path.of(System.getProperty("signaire.it.work"));
        deleteRecursively(work);
        Files.createDirectories(work);
        signSuisse = new FakeSignSuisse();
        elasticsearch = new ElasticsearchContainer(DockerImageName.parse(ELASTICSEARCH))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .withStartupTimeout(Duration.ofMinutes(5));
        postgres = new PostgreSQLContainer(DockerImageName.parse(POSTGRES));
        elasticsearch.start();
        postgres.start();

        Path bundles = work.resolve("bundles");
        try (JavaProcess cli = JavaProcess.start(jar("signaire.cli.jar"), work, "cli", List.of(
                "--signaire.signsuisse.url=" + signSuisse.uri(),
                "--spring.elasticsearch.uris=http://" + elasticsearch.getHttpHostAddress(),
                "extraire", "--categorie", "ville", "--langue", "fr", "--sortie", bundles.toString(),
                "--intervalle", "0"))) {
            assertThat(cli.await(Duration.ofMinutes(2))).as("extraction, see %s", cli.log()).isZero();
        }
        assertThat(bundles.resolve("city.zip")).exists();

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        base = URI.create("http://127.0.0.1:" + port);
        web = JavaProcess.start(jar("signaire.web.jar"), work, "web", List.of(
                "--server.port=" + port,
                "--spring.elasticsearch.uris=http://" + elasticsearch.getHttpHostAddress(),
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--signaire.bundles.directory=" + bundles,
                "--signaire.accounts.admins=admin",
                "--signaire.signsuisse.url=" + signSuisse.uri()));
        awaitSigns();
    }

    @AfterAll
    static void stopTheStack() throws InterruptedException {
        if (web != null) {
            web.close();
        }
        if (signSuisse != null) {
            signSuisse.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (elasticsearch != null) {
            elasticsearch.stop();
        }
    }

    @Test
    void findsTheSignsExtractedFromSignSuisse() throws Exception {
        Session visitor = new Session();

        String results = visitor.get("/?q=geneve").body();
        assertThat(results).contains("GENÈVE", "href=\"/signs/9002\"");

        String sign = visitor.get("/signs/9002").body();
        assertThat(sign).contains("<h1>GENÈVE</h1>", "Une ville au bout du lac.");
        Matcher video = Pattern.compile("<video[^>]* src=\"(/media/[^\"]+\\.mp4)\"").matcher(sign);
        assertThat(video.find()).as("a video served from the bundle").isTrue();

        HttpResponse<byte[]> range = visitor.client.send(HttpRequest.newBuilder(base.resolve(video.group(1)))
                .header("Range", "bytes=0-9").build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(range.statusCode()).isEqualTo(206);
        assertThat(range.body()).hasSize(10);
    }

    @Test
    void usersMakeListsAndExportThem() throws Exception {
        Session alice = Session.registered("alice");

        HttpResponse<String> created = alice.post("/lists", Map.of("name", "Villes"));
        assertThat(created.statusCode()).isEqualTo(302);
        String location = created.headers().firstValue("Location").orElseThrow();
        String list = location.substring(location.lastIndexOf('/') + 1);
        alice.post("/signs/9001/lists/" + list, Map.of());
        alice.post("/signs/9002/lists/" + list, Map.of());

        assertThat(alice.get("/lists/" + list).body()).contains("LAUSANNE", "GENÈVE");

        byte[] pdf = alice.download("/lists/" + list + "/export?format=PDF&paper=A5&perPage=2&layout=BOOKLET");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("Villes", "LAUSANNE", "GENÈVE");
        }
        byte[] anki = alice.download("/lists/" + list + "/export?format=ANKI");
        assertThat(entries(anki)).contains("collection.anki2", "media", "0");
    }

    @Test
    void administratorsEditSigns() throws Exception {
        Session admin = Session.registered("admin");

        HttpResponse<String> saved = admin.post("/admin/signs/9001", Map.of("name", "LAUSANNE", "language", "fr",
                "categories", "CITY", "definition", "La ville olympique."));

        assertThat(saved.statusCode()).isEqualTo(302);
        assertThat(new Session().get("/signs/9001").body()).contains("La ville olympique.");
    }

    /** Waits for the web application to start and to load the bundles in the background. */
    private static void awaitSigns() {
        Session visitor = new Session();
        await("signs loaded by the web application, see " + web.log())
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(1))
                .failFast("web application stopped, see " + web.log(), () -> !web.isAlive())
                // not started yet
                .ignoreExceptionsInstanceOf(IOException.class)
                .until(() -> visitor.get("/?q=lausanne").body().contains("href=\"/signs/9001\""));
    }

    private static Path jar(String property) {
        Path jar = Path.of(System.getProperty(property));
        assertThat(jar).as("built by the modules this one depends on").exists();
        return jar;
    }

    private static List<String> entries(byte[] zip) throws IOException {
        List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    /** A browser: cookies, CSRF tokens, no automatic redirects. */
    private static final class Session {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        static Session registered(String username) throws Exception {
            Session session = new Session();
            Map<String, String> account = Map.of("username", username, "displayName", username,
                    "password", "correct horse", "confirmation", "correct horse");
            assertThat(session.post("/register", account, "/register").statusCode()).isEqualTo(302);
            HttpResponse<String> login = session.post("/login", Map.of("username", username,
                    "password", "correct horse"), "/login");
            assertThat(login.headers().firstValue("Location")).hasValueSatisfying(
                    location -> assertThat(location).doesNotContain("error"));
            return session;
        }

        HttpResponse<String> get(String path) throws IOException, InterruptedException {
            return client.send(HttpRequest.newBuilder(base.resolve(path)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        byte[] download(String path) throws IOException, InterruptedException {
            HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(base.resolve(path)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            return response.body();
        }

        /** Posts a form, with the CSRF token of the home page. */
        HttpResponse<String> post(String path, Map<String, String> form) throws IOException, InterruptedException {
            return post(path, form, "/");
        }

        HttpResponse<String> post(String path, Map<String, String> form, String tokenPage)
                throws IOException, InterruptedException {
            Matcher token = CSRF.matcher(get(tokenPage).body());
            assertThat(token.find()).as("CSRF token on " + tokenPage).isTrue();
            String body = form.entrySet().stream()
                    .map(field -> encode(field.getKey()) + "=" + encode(field.getValue()))
                    .collect(Collectors.joining("&", "_csrf=" + encode(token.group(1)) + "&", ""));
            return client.send(HttpRequest.newBuilder(base.resolve(path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
