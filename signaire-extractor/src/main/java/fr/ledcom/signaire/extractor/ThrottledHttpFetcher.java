package fr.ledcom.signaire.extractor;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * Fetches resources politely: requests say who sends them ({@link UserAgent}), are spaced by a minimum interval, and
 * transient failures (network errors, 5xx and 429 responses) are retried with an increasing delay.
 *
 * <p>{@link HttpURLConnection} rather than {@code java.net.http}, which Android doesn't have. Its reads don't stop when
 * the thread is interrupted: bodies are copied by chunks, checking for it in between.
 */
@ThreadSafe
public class ThrottledHttpFetcher implements HttpFetcher {

    private static final int CONNECT_TIMEOUT_MILLIS = 30_000;
    /** Between two reads: a large video keeps coming. */
    private static final int READ_TIMEOUT_MILLIS = 120_000;
    /** A long download doesn't hold the other requests back, and the server never gets more than this at once. */
    private static final int MAX_IN_FLIGHT = 2;
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Duration minInterval;
    private final int maxAttempts;
    private final UserAgent userAgent;
    private final Supplier<Instant> clock;
    private final Sleeper sleeper;
    private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT, true);
    @GuardedBy("this")
    private @Nullable Instant lastRequest;

    /** Waits, interruptibly. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /** Reads the body of a successful response. */
    @FunctionalInterface
    private interface BodyReader<T> {
        T read(InputStream body, @Nullable String contentType) throws IOException;
    }

    public ThrottledHttpFetcher(Duration minInterval, int maxAttempts, UserAgent userAgent) {
        this(minInterval, maxAttempts, userAgent, Instant::now, duration -> Thread.sleep(duration.toMillis()));
    }

    ThrottledHttpFetcher(Duration minInterval, int maxAttempts, UserAgent userAgent, Supplier<Instant> clock,
            Sleeper sleeper) {
        this.minInterval = minInterval;
        this.maxAttempts = maxAttempts;
        this.userAgent = userAgent;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public String fetchText(URI uri) {
        return send(uri, (body, contentType) -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            copy(body, bytes);
            return bytes.toString(Charsets.of(contentType));
        });
    }

    @Override
    @SuppressFBWarnings(value = "EXS_EXCEPTION_SOFTENING_NO_CHECKED",
            justification = "ExtractionException is how extraction failures are reported")
    public void download(URI uri, Path target) {
        Path directory = Objects.requireNonNull(target.toAbsolutePath().getParent(), "target directory");
        @Nullable Path tmp = null;
        try {
            Files.createDirectories(directory);
            Path partial = Files.createTempFile(directory, ".download-", ".tmp");
            tmp = partial;
            send(uri, (body, contentType) -> {
                try (OutputStream out = Files.newOutputStream(partial)) {
                    copy(body, out);
                }
                return partial;
            });
            Files.move(partial, target, ATOMIC_MOVE, REPLACE_EXISTING);
            tmp = null;
        } catch (IOException e) {
            throw new ExtractionException("Cannot download " + uri + " to " + target, e);
        } finally {
            deleteQuietly(tmp);
        }
    }

    private <T> T send(URI uri, BodyReader<T> reader) {
        String lastFailure = "";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                pause(minInterval.multipliedBy(1L << attempt));
            }
            Attempt<T> result = attempt(uri, reader);
            T body = result.body();
            if (body != null) {
                return body;
            }
            lastFailure = result.failure();
            if (!result.worthRetrying()) {
                break;
            }
        }
        throw new ExtractionException("Cannot fetch " + uri + ": " + lastFailure);
    }

    /** What a request gave: the body, or why there is none and whether trying again may help. */
    private record Attempt<T>(@Nullable T body, String failure, boolean worthRetrying) {
    }

    private <T> Attempt<T> attempt(URI uri, BodyReader<T> reader) {
        try {
            inFlight.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtractionException("Interrupted while fetching " + uri, e);
        }
        try {
            throttle();
            HttpURLConnection connection = open(uri);
            try {
                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    try (InputStream body = connection.getInputStream()) {
                        return new Attempt<>(reader.read(body, connection.getContentType()), "", false);
                    }
                }
                return new Attempt<>(null, "HTTP status " + status, status == 429 || status >= 500);
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            // a timeout is an InterruptedIOException too: only the interruption of the thread stops the retries
            if (Thread.currentThread().isInterrupted()) {
                throw new ExtractionException("Interrupted while fetching " + uri, e);
            }
            return new Attempt<>(null, e.toString(), true);
        } finally {
            inFlight.release();
        }
    }

    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD",
            justification = "SignSuisseClient builds the URIs from the address of SignSuisse it is configured with")
    private HttpURLConnection open(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", userAgent.value());
        return connection;
    }

    /** Copies a body, stopping when the thread is interrupted. */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Interrupted");
            }
            out.write(buffer, 0, read);
        }
    }

    /** Spaces out the starts of the requests, whatever the number of threads sending them. */
    private synchronized void throttle() {
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

    private static void deleteQuietly(@Nullable Path file) {
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
