package fr.ledcom.signaire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The standalone application, as a user runs it on their computer: no container, no server. Set up on its first
 * run, it downloads signs from a (fake) SignSuisse, keeps everything for the next runs, and stops from its pages.
 * The setup with several accounts is a test of the web module ({@code StandalonePagesTest}): it needs no jar.
 */
class StandaloneIT {

    private Path work;
    private FakeSignSuisse signSuisse;
    private int port;
    private URI base;
    private JavaProcess standalone;

    @BeforeEach
    void freshComputer() throws IOException {
        work = Path.of(System.getProperty("signaire.it.work")).resolveSibling("it-standalone");
        JavaProcess.deleteRecursively(work);
        Files.createDirectories(work);
        signSuisse = new FakeSignSuisse();
        port = JavaProcess.freePort();
        base = URI.create("http://127.0.0.1:" + port);
    }

    @AfterEach
    void stop() throws InterruptedException {
        if (standalone != null) {
            standalone.close();
        }
        signSuisse.close();
    }

    @Test
    void aloneOnItsComputer() throws Exception {
        standalone = start("standalone");
        Session me = new Session(base);
        awaitStarted(me);

        // the first run: the setup, whatever the page
        assertThat(me.get("/lists").headers().firstValue("Location")).hasValue(base + "/setup");
        HttpResponse<String> setup = me.post("/setup", Map.of("alone", "true", "categories", "CITY",
                "languages", "fr", "media", "ALL"), "/setup");
        assertThat(setup.headers().firstValue("Location")).hasValue(base + "/admin/sync");

        // the signs are downloaded in the background
        await("signs downloaded, see " + standalone.log())
                .atMost(Duration.ofMinutes(2))
                .until(() -> me.get("/?q=geneve").body().contains("href=\"/signs/9002\""));
        // no account: lists right away, and the administration
        HttpResponse<String> list = me.post("/lists", Map.of("name", "Villes"));
        assertThat(list.statusCode()).isEqualTo(302);
        String location = list.headers().firstValue("Location").orElseThrow();
        me.post("/signs/9002/lists/" + location.substring(location.lastIndexOf('/') + 1), Map.of());
        assertThat(me.get("/admin").statusCode()).isEqualTo(200);

        // started again while it runs: it only opens the browser
        try (JavaProcess again = start("again")) {
            assertThat(again.await(Duration.ofMinutes(1))).isZero();
        }
        assertThat(standalone.isAlive()).isTrue();

        assertThat(me.post("/admin/quit", Map.of()).body()).contains("Signaire est arrêté");
        assertThat(standalone.await(Duration.ofMinutes(1))).as("exit code, see %s", standalone.log()).isZero();

        // the next run: no setup, the same signs and lists
        standalone = start("standalone-next");
        Session later = new Session(base);
        awaitStarted(later);
        assertThat(later.get("/").statusCode()).isEqualTo(200);
        assertThat(later.get(location).body()).contains("Villes", "GENÈVE");
        assertThat(Files.readString(work.resolve("config/signaire/standalone.properties")))
                .contains("signaire.accounts.single-user=true");
        assertThat(work.resolve("state/signaire/database")).isDirectoryContaining("glob:**.mv.db");
    }

    private JavaProcess start(String name) throws IOException {
        return JavaProcess.start(JavaProcess.jar("signaire.standalone.jar"), work, name, List.of(
                "--server.port=" + port,
                "--signaire.standalone.open-browser=false",
                "--signaire.signsuisse.url=" + signSuisse.uri(),
                "--signaire.sync.interval=0ms"));
    }

    private void awaitStarted(Session session) {
        await("standalone application started, see " + standalone.log())
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofMillis(500))
                .failFast("standalone application stopped, see " + standalone.log(), () -> !standalone.isAlive())
                .ignoreExceptionsInstanceOf(IOException.class)
                .until(() -> session.get("/help").statusCode() == 200);
    }
}
