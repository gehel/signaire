package fr.ledcom.signaire.web;

import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.media.MediaLibrary;
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.extractor.CategoryExtractor;
import fr.ledcom.signaire.extractor.ExtractionException;
import fr.ledcom.signaire.extractor.ExtractionListener;
import fr.ledcom.signaire.extractor.ExtractionReport;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignEnumerator;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.extractor.SignSuisseClient;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/**
 * Synchronizes the bundles with SignSuisse, in the background: extracts categories into the bundle directory,
 * loading each bundle as soon as it is written. One synchronization at a time; it can be cancelled.
 */
@Component
public class SignSuisseSync {

    private static final Logger LOG = System.getLogger(SignSuisseSync.class.getName());
    private static final int MAX_QUERY_LENGTH = 6;

    private final SignSource source;
    private final BundleLoader loader;
    private final MediaLibrary media;
    private final SignEditor editor;
    private final Path bundles;
    private final Path mediaCache;
    private final AtomicReference<Progress> progress = new AtomicReference<>(Progress.IDLE);
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private Thread worker;

    public SignSuisseSync(SignSource source, BundleLoader loader, MediaLibrary media, SignEditor editor,
            @Value("${signaire.bundles.directory:bundles}") Path bundles,
            @Value("${signaire.media.cache:bundles/.media}") Path mediaCache) {
        this.source = source;
        this.loader = loader;
        this.media = media;
        this.editor = editor;
        this.bundles = bundles;
        this.mediaCache = mediaCache;
    }

    /** States of a synchronization. */
    public enum State {
        IDLE, RUNNING, DONE, CANCELLED, FAILED
    }

    /**
     * The outcome of a category.
     *
     * @param complete false when the search may have missed signs
     */
    public record CategoryResult(Category category, int signs, int failures, boolean complete) {
    }

    /**
     * Where the synchronization is.
     *
     * @param category the category being extracted, or null
     * @param language the language being extracted, or null
     * @param listing whether the signs of the category and language are still being searched
     * @param searches the searches sent for the current category and language
     * @param found signs found in the current category and language (so far, while listing)
     * @param extracted signs extracted so far
     * @param failed signs that could not be extracted so far
     * @param done the categories done
     * @param error why the synchronization failed, or null
     */
    public record Progress(State state, Category category, Language language, boolean listing, int searches,
            int found, int extracted, int failed, List<CategoryResult> done, String error) {

        static final Progress IDLE = new Progress(State.IDLE, null, null, false, 0, 0, 0, 0, List.of(), null);

        public Progress {
            done = List.copyOf(done);
        }

        public boolean running() {
            return state == State.RUNNING;
        }

        Progress with(State newState, String newError) {
            return new Progress(newState, null, null, false, 0, found, extracted, failed, done, newError);
        }
    }

    public Progress progress() {
        return progress.get();
    }

    /**
     * Starts a synchronization, unless one is running.
     *
     * @return false when a synchronization is already running
     */
    public synchronized boolean start(Collection<Category> categories, Collection<Language> languages,
            boolean withMedia) {
        if (progress.get().running()) {
            return false;
        }
        Set<Category> selectedCategories = categories.isEmpty() ? EnumSet.allOf(Category.class)
                : EnumSet.copyOf(categories);
        Set<Language> selectedLanguages = languages.isEmpty() ? EnumSet.allOf(Language.class)
                : EnumSet.copyOf(languages);
        cancelled.set(false);
        progress.set(new Progress(State.RUNNING, null, null, false, 0, 0, 0, 0, List.of(), null));
        worker = Thread.ofVirtual().name("signsuisse-sync")
                .start(() -> run(selectedCategories, selectedLanguages, withMedia));
        return true;
    }

    /** Stops the synchronization after the current search or sign; the categories done stay loaded. */
    public void cancel() {
        cancelled.set(true);
    }

    /** Waits for the end of the synchronization; returns false on timeout. */
    public boolean awaitCompletion(Duration timeout) throws InterruptedException {
        Thread running;
        synchronized (this) {
            running = worker;
        }
        return running == null || running.join(timeout);
    }

    private void run(Set<Category> categories, Set<Language> languages, boolean withMedia) {
        CategoryExtractor extractor = new CategoryExtractor(source,
                new SignEnumerator(source, SignSuisseClient.MAX_RESULTS, MAX_QUERY_LENGTH),
                new BundleWriter(Clock.systemUTC()), new Listener());
        try {
            for (Category category : categories) {
                ExtractionReport report = extractor.extract(category, languages, bundles, mediaCache, withMedia);
                loader.load(report.bundle());
                progress.updateAndGet(p -> {
                    List<CategoryResult> done = new ArrayList<>(p.done());
                    done.add(new CategoryResult(category, report.signCount(), report.failures().size(),
                            report.enumerationComplete()));
                    return new Progress(State.RUNNING, null, null, false, 0, 0, p.extracted(), p.failed(), done,
                            null);
                });
            }
            progress.updateAndGet(p -> p.with(State.DONE, null));
        } catch (CancellationException e) {
            progress.updateAndGet(p -> p.with(State.CANCELLED, null));
        } catch (ExtractionException | BundleException | SearchException | UncheckedIOException e) {
            LOG.log(Level.ERROR, "SignSuisse synchronization failed", e);
            progress.updateAndGet(p -> p.with(State.FAILED, e.getMessage()));
        } finally {
            media.refresh();
            editor.reapply();
        }
    }

    /** Follows the extraction, and stops it when cancelled. */
    private final class Listener implements ExtractionListener {
        @Override
        public void searched(Category category, Language language, int queries, int found) {
            checkCancelled();
            progress.updateAndGet(p -> new Progress(State.RUNNING, category, language, true, queries, found,
                    p.extracted(), p.failed(), p.done(), null));
        }

        @Override
        public void enumerated(Category category, Language language, SignEnumerator.Result result) {
            checkCancelled();
            progress.updateAndGet(p -> new Progress(State.RUNNING, category, language, false, p.searches(),
                    result.hits().size(), p.extracted(), p.failed(), p.done(), null));
        }

        @Override
        public void signExtracted(Sign sign) {
            checkCancelled();
            progress.updateAndGet(p -> new Progress(State.RUNNING, p.category(), p.language(), false, p.searches(),
                    p.found(), p.extracted() + 1, p.failed(), p.done(), null));
        }

        @Override
        public void signFailed(SearchHit hit, RuntimeException cause) {
            checkCancelled();
            progress.updateAndGet(p -> new Progress(State.RUNNING, p.category(), p.language(), false, p.searches(),
                    p.found(), p.extracted(), p.failed() + 1, p.done(), null));
        }

        private void checkCancelled() {
            if (cancelled.get()) {
                throw new CancellationException("Synchronization cancelled");
            }
        }
    }
}
