package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

/** The parts of the standalone application that need no Spring context. */
class StandaloneApplicationTest {

    @TempDir
    Path config;

    @Test
    void theStandaloneApplicationHasItsPort() {
        assertThat(StandaloneApplication.port()).isEqualTo(StandaloneApplication.DEFAULT_PORT);
        assertThat(StandaloneApplication.port("--server.port=9000", "--signaire.x=y")).isEqualTo(9000);
    }

    @Test
    void findsWhetherSignaireAlreadyRuns() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            assertThat(StandaloneApplication.inUse(server.getLocalPort())).isTrue();
        }
        int free;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            free = server.getLocalPort();
        }
        assertThat(StandaloneApplication.inUse(free)).isFalse();
    }

    @Test
    void opensTheBrowserOfEachSystem() {
        URI uri = URI.create("http://localhost:8180/");

        assertThat(Browser.command("Windows 11", uri)).contains(List.of("rundll32", "url.dll,FileProtocolHandler",
                "http://localhost:8180/"));
        assertThat(Browser.command("Mac OS X", uri)).contains(List.of("open", "http://localhost:8180/"));
        assertThat(Browser.command("Linux", uri)).contains(List.of("xdg-open", "http://localhost:8180/"));
        assertThat(Browser.command("Plan 9", uri)).isEmpty();
        assertThat(Browser.command(null, uri)).isEmpty();
    }

    @Test
    void quittingStopsTheApplication() throws InterruptedException {
        CountDownLatch stopped = new CountDownLatch(1);
        QuitController quit = new QuitController(new Standalone(true, true, true, config), stopped::countDown);

        assertThat(quit.quit()).isEqualTo("quit");
        assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void serversCannotBeStoppedFromTheirPages() {
        QuitController quit = new QuitController(new Standalone(false, false, false, config), () -> {
            throw new AssertionError("stopped");
        });

        assertThatThrownBy(quit::quit).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void theStandaloneConfigurationOpensTheBrowser() {
        assertThat(new StandaloneConfiguration().browser()).isNotNull();
    }
}
