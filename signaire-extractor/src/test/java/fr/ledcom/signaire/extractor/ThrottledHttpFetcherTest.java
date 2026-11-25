package fr.ledcom.signaire.extractor;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/page");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/latin", exchange -> {
            byte[] bytes = "héllo".getBytes(ISO_8859_1);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=ISO-8859-1");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        fetcher = new ThrottledHttpFetcher(INTERVAL, 3, AGENT, time::now, time::sleep);
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

        URI flaky = uri("/flaky");

        assertThatThrownBy(() -> fetcher.fetchText(flaky))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("503");
    }

    @Test
    void doesNotRetryClientErrors() {
        URI missing = uri("/missing");

        assertThatThrownBy(() -> fetcher.fetchText(missing))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("404");
    }

    @Test
    void followsRedirects() {
        assertThat(fetcher.fetchText(uri("/moved"))).isEqualTo("héllo");
    }

    @Test
    void readsTextInTheCharsetOfTheResponse() {
        assertThat(fetcher.fetchText(uri("/latin"))).isEqualTo("héllo");
    }

    /** A cancelled extraction interrupts its downloads, which stop at once. */
    @Test
    @Timeout(10)
    void interruptedDownloadsStopAndLeaveNoFile() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        server.createContext("/endless", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < 1000; i++) {
                    out.write(new byte[1024]);
                    out.flush();
                    started.countDown();
                    Thread.sleep(10);
                }
            } catch (IOException | InterruptedException ignored) {
                // the client went away
            }
        });
        Path target = tmp.resolve("endless.mp4");
        AtomicReference<ExtractionException> failure = new AtomicReference<>();
        Thread download = new Thread(() -> {
            try {
                fetcher.download(uri("/endless"), target);
            } catch (ExtractionException e) {
                failure.set(e);
            }
        });
        download.start();
        started.await();
        download.interrupt();
        download.join(Duration.ofSeconds(3));

        assertThat(download.isAlive()).isFalse();
        assertThat(failure.get()).isNotNull();
        assertThat(target).doesNotExist();
        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void failedDownloadsLeaveNoFile() {
        Path target = tmp.resolve("file.mp4");
        URI missing = uri("/missing");

        assertThatThrownBy(() -> fetcher.download(missing, target)).isInstanceOf(ExtractionException.class);

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
    @Test
    @Timeout(20)
    void keepsAtMostTwoRequestsInFlightWithTheirStartsSpacedOut() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        HttpServer slow = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        slow.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        // a response that takes 300 ms
        CountDownLatch never = new CountDownLatch(1);
        slow.createContext("/slow", exchange -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                never.await(300, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            respond(exchange, 200, "slow");
        });
        slow.start();
        try (ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor()) {
            URI uri = URI.create("http://localhost:" + slow.getAddress().getPort() + "/slow");
            List<Future<String>> pages = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                pages.add(clients.submit(() -> fetcher.fetchText(uri)));
            }
            for (Future<String> page : pages) {
                assertThat(page.get()).isEqualTo("slow");
            }
        } finally {
            slow.stop(0);
        }

        assertThat(maxInFlight).hasValue(2);
        // the starts of the 5 requests are spaced out
        assertThat(time.slept).isGreaterThanOrEqualTo(INTERVAL.multipliedBy(4));
    }

    private static final class FakeTime {
        private Instant now = Instant.parse("2026-11-17T20:00:00Z");
        private Duration slept = Duration.ZERO;

        synchronized Instant now() {
            return now;
        }

        synchronized void sleep(Duration duration) {
            now = now.plus(duration);
            slept = slept.plus(duration);
        }
    }
}
