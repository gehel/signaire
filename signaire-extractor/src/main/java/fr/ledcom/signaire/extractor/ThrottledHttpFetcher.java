package fr.ledcom.signaire.extractor;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Fetches resources politely: requests say who sends them ({@link UserAgent}), are spaced by a minimum interval, and
 * transient failures (network errors, 5xx and 429 responses) are retried with an increasing delay.
 */
public class ThrottledHttpFetcher implements HttpFetcher {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient client;
    private final Duration minInterval;
    private final int maxAttempts;
    private final UserAgent userAgent;
    private final Supplier<Instant> clock;
    private final Sleeper sleeper;
    private Instant lastRequest;

    /** Waits, interruptibly. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public ThrottledHttpFetcher(HttpClient client, Duration minInterval, int maxAttempts, UserAgent userAgent) {
        this(client, minInterval, maxAttempts, userAgent, Instant::now, duration -> Thread.sleep(duration.toMillis()));
    }

    ThrottledHttpFetcher(HttpClient client, Duration minInterval, int maxAttempts, UserAgent userAgent,
            Supplier<Instant> clock, Sleeper sleeper) {
        this.client = client;
        this.minInterval = minInterval;
        this.maxAttempts = maxAttempts;
        this.userAgent = userAgent;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public String fetchText(URI uri) {
        return send(uri, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Override
    @SuppressFBWarnings(value = "EXS_EXCEPTION_SOFTENING_NO_CHECKED",
            justification = "ExtractionException is how extraction failures are reported")
    public void download(URI uri, Path target) {
        Path directory = Objects.requireNonNull(target.toAbsolutePath().getParent(), "target directory");
        Path tmp = null;
        try {
            Files.createDirectories(directory);
            tmp = Files.createTempFile(directory, ".download-", ".tmp");
            send(uri, HttpResponse.BodyHandlers.ofFile(tmp));
            Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING);
            tmp = null;
        } catch (IOException e) {
            throw new ExtractionException("Cannot download " + uri + " to " + target, e);
        } finally {
            deleteQuietly(tmp);
        }
    }

    private synchronized <T> HttpResponse<T> send(URI uri, BodyHandler<T> bodyHandler) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", userAgent.value())
                .timeout(TIMEOUT)
                .GET()
                .build();
        String lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                pause(minInterval.multipliedBy(1L << attempt));
            }
            throttle();
            try {
                HttpResponse<T> response = client.send(request, bodyHandler);
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response;
                }
                lastFailure = "HTTP status " + status;
                if (status != 429 && status < 500) {
                    break;
                }
            } catch (IOException e) {
                lastFailure = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExtractionException("Interrupted while fetching " + uri, e);
            }
        }
        throw new ExtractionException("Cannot fetch " + uri + ": " + lastFailure);
    }

    private void throttle() {
        Instant now = clock.get();
        if (lastRequest != null) {
            Duration elapsed = Duration.between(lastRequest, now);
            if (elapsed.compareTo(minInterval) < 0) {
                pause(minInterval.minus(elapsed));
            }
        }
        lastRequest = clock.get();
    }

    private void pause(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtractionException("Interrupted", e);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // best effort cleanup of the temporary file, the original error matters more
        }
    }
}
