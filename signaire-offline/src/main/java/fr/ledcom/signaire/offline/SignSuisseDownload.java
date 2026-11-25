package fr.ledcom.signaire.offline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.extractor.CategoryExtractor;
import fr.ledcom.signaire.extractor.ExtractionListener;
import fr.ledcom.signaire.extractor.ExtractionOptions;
import fr.ledcom.signaire.extractor.ExtractionReport;
import fr.ledcom.signaire.extractor.ExtractionStatus;
import fr.ledcom.signaire.extractor.SignEnumerator;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.extractor.SignSuisseClient;
import fr.ledcom.signaire.extractor.ThrottledHttpFetcher;
import fr.ledcom.signaire.extractor.UserAgent;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import net.jcip.annotations.ThreadSafe;

/**
 * Downloads categories from SignSuisse into the library, as {@code signaire extraire} does: a bundle per category,
 * loaded as soon as it is written. Once cancelled, a download stays so: make another one.
 */
@ThreadSafe
public final class SignSuisseDownload {

    /** As the command line: request starts 500 ms apart. */
    private static final Duration INTERVAL = Duration.ofMillis(500);
    private static final int MAX_ATTEMPTS = 3;

    private final CategoryExtractor extractor;
    private final SignLibrary library;
    private final Path cache;

    /**
     * @param cache where media are downloaded before being written in their bundle; those already there aren't
     *              downloaded again
     */
    public SignSuisseDownload(SignSource source, SignLibrary library, Path cache, ExtractionListener listener) {
        this.extractor = new CategoryExtractor(source, SignEnumerator.forSignSuisse(source),
                new BundleWriter(Clock.systemUTC()), listener);
        this.library = library;
        this.cache = cache;
    }

    /**
     * From SignSuisse, politely.
     *
     * @param server SignSuisse itself, or the fake one of a test (see {@link #server})
     */
    public static SignSuisseDownload fromSignSuisse(URI server, SignLibrary library, Path cache, UserAgent userAgent,
            ExtractionListener listener) {
        SignSource signSuisse = new SignSuisseClient(new ThrottledHttpFetcher(INTERVAL, MAX_ATTEMPTS, userAgent),
                server);
        return new SignSuisseDownload(signSuisse, library, cache, listener);
    }

    /**
     * The server to download from: SignSuisse, unless a file names another one, as the tests of the app do to point
     * it at a fake SignSuisse.
     *
     * @throws IllegalArgumentException when the file names no {@code http} or {@code https} server: the download
     *                                  stops, rather than going to SignSuisse when a test wanted another server
     * @throws UncheckedIOException when the file can't be read
     */
    public static URI server(Path file) {
        if (!Files.exists(file)) {
            return SignSuisseClient.DEFAULT_BASE_URI;
        }
        String content;
        try {
            // not Files.readString: Android lacks it
            content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            URI server = new URI(content);
            String scheme = server.getScheme();
            if (("http".equals(scheme) || "https".equals(scheme)) && server.getHost() != null) {
                return server;
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(file + " names no server: " + content, e);
        }
        throw new IllegalArgumentException(file + " names no server: " + content);
    }

    /** Downloads categories one after the other. */
    public List<ExtractionReport> download(Collection<Category> categories, Collection<Language> languages,
            ExtractionOptions options) {
        List<ExtractionReport> reports = new ArrayList<>();
        for (Category category : categories) {
            reports.add(extractor.extract(category, languages, library.directory(), cache, options));
            library.load();
        }
        return reports;
    }

    /** Where the current (or last) category is. */
    public Optional<ExtractionStatus> status() {
        return extractor.status();
    }

    /** Stops the download: {@link #download} throws a CancellationException. */
    public void cancel() {
        extractor.cancel();
    }
}
