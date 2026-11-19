package fr.ledcom.signaire.extractor;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@SuppressForbidden(reason = "jdk.httpserver is a supported JDK module, flagged because of its com.sun package")
class ThrottledHttpFetcherTest {

    private static final UserAgent AGENT = UserAgent.of("alice@example.org", null);

    private static final Duration INTERVAL = Duration.ofMillis(500);

    @TempDir
    Path tmp;

    private HttpServer server;
    private final AtomicInteger failuresBeforeSuccess = new AtomicInteger();
    private final List<String> userAgents = new ArrayList<>();
    private final FakeTime time = new FakeTime();
    private ThrottledHttpFetcher fetcher;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/page", exchange -> respond(exchange, 200, "héllo"));
        server.createContext("/missing", exchange -> respond(exchange, 404, "not found"));
        server.createContext("/flaky", exchange -> {
            if (failuresBeforeSuccess.getAndDecrement() > 0) {
                respond(exchange, 503, "busy");
            } else {
                respond(exchange, 200, "finally");
            }
        });
        server.start();
        fetcher = new ThrottledHttpFetcher(HttpClient.newHttpClient(), INTERVAL, 3, AGENT, time::now, time::sleep);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fetchesText() {
        assertThat(fetcher.fetchText(uri("/page"))).isEqualTo("héllo");
        assertThat(userAgents).containsExactly(AGENT.value());
    }

    @Test
    void downloadsFiles() {
        Path target = tmp.resolve("media/file.mp4");

        fetcher.download(uri("/page"), target);

        assertThat(target).hasContent("héllo");
    }

    @Test
    void waitsBetweenRequests() {
        fetcher.fetchText(uri("/page"));
        fetcher.fetchText(uri("/page"));
        fetcher.fetchText(uri("/page"));

        assertThat(time.slept).isGreaterThanOrEqualTo(INTERVAL.multipliedBy(2));
    }

    @Test
    void retriesServerErrors() {
        failuresBeforeSuccess.set(2);

        assertThat(fetcher.fetchText(uri("/flaky"))).isEqualTo("finally");
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttempts() {
        failuresBeforeSuccess.set(3);

        assertThatThrownBy(() -> fetcher.fetchText(uri("/flaky")))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("503");
    }

    @Test
    void doesNotRetryClientErrors() {
        assertThatThrownBy(() -> fetcher.fetchText(uri("/missing")))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("404");
    }

    @Test
    void failedDownloadsLeaveNoFile() {
        Path target = tmp.resolve("file.mp4");

        assertThatThrownBy(() -> fetcher.download(uri("/missing"), target)).isInstanceOf(ExtractionException.class);

        assertThat(target).doesNotExist();
        assertThat(tmp).isEmptyDirectory();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        synchronized (userAgents) {
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
        }
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Time that only advances when sleeping. */
    private static final class FakeTime {
        private Instant now = Instant.parse("2026-11-17T20:00:00Z");
        private Duration slept = Duration.ZERO;

        Instant now() {
            return now;
        }

        void sleep(Duration duration) {
            now = now.plus(duration);
            slept = slept.plus(duration);
        }
    }
}
