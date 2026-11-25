package fr.ledcom.signaire.it;

import static fr.ledcom.signaire.model.TestSigns.GARE;
import static fr.ledcom.signaire.model.TestSigns.GENEVE;
import static fr.ledcom.signaire.model.TestSigns.LAUSANNE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import fr.ledcom.signaire.extractor.FakeSignSuisse;

/**
 * The whole stack, as users run it: the command line extracts signs from a (fake) SignSuisse into bundles, and the
 * web application, a separate process with its Lucene index and PostgreSQL, loads them; the tests are users of the
 * web application, through HTTP.
 */
class FullStackIT {

    /** Keep in line with compose.yaml. */
    private static final String POSTGRES = "postgres:18-alpine";

    private static Path work;
    private static FakeSignSuisse signSuisse;
    private static PostgreSQLContainer postgres;
    private static JavaProcess web;
    private static URI base;
    private static Path bundles;

    @BeforeAll
    static void startTheStack() throws Exception {
        work = Path.of(System.getProperty("signaire.it.work"));
        JavaProcess.deleteRecursively(work);
        Files.createDirectories(work);
        signSuisse = new FakeSignSuisse();
        postgres = new PostgreSQLContainer(DockerImageName.parse(POSTGRES));
        postgres.start();

        bundles = work.resolve("bundles");
        extract("cli");

        int port = JavaProcess.freePort();
        base = URI.create("http://127.0.0.1:" + port);
        web = JavaProcess.start(JavaProcess.jar("signaire.web.jar"), work, "web", List.of(
                "--server.port=" + port,
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--signaire.bundles.directory=" + bundles,
                "--signaire.accounts.admins=admin,curator",
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
    }

    @Test
    void findsTheSignsExtractedFromSignSuisse() throws Exception {
        Session visitor = new Session(base);

        String results = visitor.get("/?q=gare").body();
        assertThat(results).contains("GARE", "href=\"/signs/" + GARE.uid() + "\"");

        String sign = visitor.get("/signs/" + GARE.uid()).body();
        assertThat(sign).contains("<h1>GARE</h1>", "Le train part de la gare à midi.");
        Matcher video = Pattern.compile("<video[^>]* src=\"(/media/[^\"]+\\.mp4)\"").matcher(sign);
        assertThat(video.find()).as("a video served from the bundle").isTrue();

        HttpResponse<byte[]> range = visitor.client.send(HttpRequest.newBuilder(base.resolve(video.group(1)))
                .header("Range", "bytes=0-9").build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(range.statusCode()).isEqualTo(206);
        assertThat(range.body()).hasSize(10);
    }

    @Test
    void usersMakeListsAndExportThem() throws Exception {
        Session alice = Session.registered(base, "alice");

        HttpResponse<String> created = alice.post("/lists", Map.of("name", "Villes"));
        assertThat(created.statusCode()).isEqualTo(302);
        String location = created.headers().firstValue("Location").orElseThrow();
        String list = location.substring(location.lastIndexOf('/') + 1);
        alice.post("/signs/" + LAUSANNE.uid() + "/lists/" + list, Map.of());
        // GARE has a photo, for the cards of Anki
        alice.post("/signs/" + GARE.uid() + "/lists/" + list, Map.of());

        assertThat(alice.get("/lists/" + list).body()).contains("LAUSANNE", "GARE");

        byte[] pdf = alice.download("/lists/" + list + "/export?format=PDF&paper=A5&perPage=2&layout=BOOKLET");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("Villes", "LAUSANNE", "GARE");
        }
        byte[] anki = alice.download("/lists/" + list + "/export?format=ANKI");
        assertThat(entries(anki)).contains("collection.anki2", "media", "0");
    }

    @Test
    void administratorsEditSigns() throws Exception {
        Session admin = Session.registered(base, "admin");

        HttpResponse<String> saved = admin.post("/admin/signs/" + LAUSANNE.uid(), Map.of("name", "LAUSANNE",
                "language", "fr",
                "categories", "CITY", "definition", "La ville olympique."));

        assertThat(saved.statusCode()).isEqualTo(302);
        assertThat(new Session(base).get("/signs/" + LAUSANNE.uid()).body()).contains("La ville olympique.");
    }

    /**
     * Signs are transient: the bundles and the index can be deleted and made again from SignSuisse at any time.
     * Everything that refers to signs (lists, changes of administrators, requests) keeps working, because signs are
     * identified by their SignSuisse uid, or by the uid given by Signaire to the signs it added.
     */
    @Test
    void everythingSurvivesTheDeletionAndExtractionOfAllTheSigns() throws Exception {
        Session carol = Session.registered(base, "carol");
        Session curator = Session.registered(base, "curator");
        String location = carol.post("/lists", Map.of("name", "Genève")).headers().firstValue("Location")
                .orElseThrow();
        String list = location.substring(location.indexOf("/lists/"));
        carol.post("/signs/" + GENEVE.uid() + list, Map.of());
        // LAUSANNE, as administratorsEditSigns: the form drops the media of the sign, that other tests export
        curator.post("/admin/signs/" + LAUSANNE.uid(), Map.of("name", "LAUSANNE", "language", "fr",
                "categories", "CITY", "definition", "La ville du CIO."));
        String added = curator.post("/admin/signs", Map.of("name", "SIGNAIRE", "language", "fr",
                "definition", "Ce logiciel.")).headers().firstValue("Location").orElseThrow();
        String addedUid = added.substring(added.lastIndexOf('/') + 1);
        assertThat(addedUid).startsWith("local-");
        carol.post("/requests", Map.of("word", "Signaire", "language", "fr"));
        Matcher request = Pattern.compile("/admin/requests/(\\d+)/reject")
                .matcher(curator.get("/admin/requests").body());
        assertThat(request.find()).as("the request of carol").isTrue();
        curator.post("/admin/requests/" + request.group(1) + "/link", Map.of("uid", addedUid));

        // all the signs are gone
        try (var files = Files.list(bundles)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".zip")).toList()) {
                Files.delete(file);
            }
        }
        curator.post("/admin/bundles/reindex", Map.of());
        assertThat(carol.get("/signs/" + GENEVE.uid()).statusCode()).isEqualTo(404);
        assertThat(carol.get(list).body()).contains("Signes absents du lexique : 1.");

        // and extracted again from SignSuisse
        extract("cli-again");
        curator.post("/admin/bundles/reload", Map.of());

        assertThat(carol.get(list).body()).contains("GENÈVE").doesNotContain("absents");
        assertThat(carol.get("/signs/" + LAUSANNE.uid()).body()).contains("La ville du CIO.");
        assertThat(carol.get(added).body()).contains("SIGNAIRE", "Ce logiciel.");
        assertThat(carol.get("/requests").body()).contains("href=\"/signs/" + addedUid + "\"");
    }

    /** Extracts the signs of the fake SignSuisse into the bundles, with the command line. */
    private static void extract(String name) throws IOException, InterruptedException {
        try (JavaProcess cli = JavaProcess.start(JavaProcess.jar("signaire.cli.jar"), work, name, List.of(
                "--signaire.signsuisse.url=" + signSuisse.uri(),
                "extraire", "--categorie", "ville", "--langue", "fr", "--sortie", bundles.toString(),
                "--intervalle", "0"))) {
            assertThat(cli.await(Duration.ofMinutes(2))).as("extraction, see %s", cli.log()).isZero();
        }
        assertThat(bundles.resolve("city.zip")).exists();
    }

    /** Waits for the web application to start and to load the bundles in the background. */
    private static void awaitSigns() {
        Session visitor = new Session(base);
        await("signs loaded by the web application, see " + web.log())
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(1))
                .failFast("web application stopped, see " + web.log(), () -> !web.isAlive())
                // not started yet
                .ignoreExceptionsInstanceOf(IOException.class)
                .until(() -> visitor.get("/?q=lausanne").body().contains("href=\"/signs/" + LAUSANNE.uid() + "\""));
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

}
