package fr.ledcom.signaire.web;

import java.io.UncheckedIOException;
import java.net.URI;
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
    /** Signs between two progress messages in the log. */
    private static final int LOG_EVERY = 25;

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
     * @param plannedSearches the searches planned so far, whose number grows while listing
     * @param found signs found in the current category and language (so far, while listing)
     * @param extracted signs extracted so far, with their media when they are downloaded
     * @param failed signs that could not be extracted so far
     * @param mediaQueued media of the signs read so far that were not downloaded yet
     * @param mediaDownloaded media downloaded so far
     * @param done the categories done
     * @param error why the synchronization failed, or null
     */
    public record Progress(State state, Category category, Language language, boolean listing, int searches,
            int plannedSearches, int found, int extracted, int failed, int mediaQueued, int mediaDownloaded, List<CategoryResult> done,
            String error) {

        static final Progress IDLE = new Progress(State.IDLE, null, null, false, 0, 0, 0, 0, 0, 0, 0, List.of(),
                null);
        static final Progress STARTED = IDLE.with(State.RUNNING, null);

        public Progress {
            done = List.copyOf(done);
        }

        public boolean running() {
            return state == State.RUNNING;
        }

        Progress with(State newState, String newError) {
            return new Progress(newState, null, null, false, 0, 0, found, extracted, failed, mediaQueued,
                    mediaDownloaded, done, newError);
        }

        Progress listing(Category newCategory, Language newLanguage, int newSearches, int planned, int newFound) {
            return new Progress(state, newCategory, newLanguage, true, newSearches, planned, newFound, extracted,
                    failed, mediaQueued, mediaDownloaded, done, error);
        }

        Progress listed(int newFound) {
            return new Progress(state, category, language, false, searches, 0, newFound, extracted, failed,
                    mediaQueued, mediaDownloaded, done, error);
        }

        Progress plusExtracted() {
            return new Progress(state, category, language, false, searches, 0, found, extracted + 1, failed,
                    mediaQueued, mediaDownloaded, done, error);
        }

        Progress plusFailed() {
            return new Progress(state, category, language, false, searches, 0, found, extracted, failed + 1,
                    mediaQueued, mediaDownloaded, done, error);
        }

        Progress plusMedia(int queued, int downloaded) {
            return new Progress(state, category, language, listing, searches, plannedSearches, found, extracted,
                    failed, mediaQueued + queued, mediaDownloaded + downloaded, done, error);
        }

        Progress plusDone(CategoryResult result) {
            List<CategoryResult> newDone = new ArrayList<>(done);
            newDone.add(result);
            return new Progress(state, null, null, false, 0, 0, 0, extracted, failed, mediaQueued, mediaDownloaded,
                    newDone, error);
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
        progress.set(Progress.STARTED);
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
                LOG.log(Level.INFO, "{0}: {1} signs written to {2} and loaded, {3} failed", category,
                        report.signCount(), report.bundle(), report.failures().size());
                progress.updateAndGet(p -> p.plusDone(new CategoryResult(category, report.signCount(),
                        report.failures().size(), report.enumerationComplete())));
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
        public void searched(Category category, Language language, int queries, int planned, int found) {
            checkCancelled();
            progress.updateAndGet(p -> p.listing(category, language, queries, planned, found));
        }

        @Override
        public void enumerated(Category category, Language language, SignEnumerator.Result result) {
            checkCancelled();
            LOG.log(Level.INFO, "{0} ({1}): {2} signs found in {3} searches", category, language.code(),
                    result.hits().size(), result.queries());
            progress.updateAndGet(p -> p.listed(result.hits().size()));
        }

        @Override
        public void mediaQueued(Sign sign, int count) {
            checkCancelled();
            progress.updateAndGet(p -> p.plusMedia(count, 0));
        }

        @Override
        public void mediaDownloaded(URI uri) {
            checkCancelled();
            progress.updateAndGet(p -> p.plusMedia(0, 1));
        }

        @Override
        public void signExtracted(Sign sign) {
            checkCancelled();
            logEvery(progress.updateAndGet(Progress::plusExtracted));
        }

        @Override
        public void signFailed(SearchHit hit, RuntimeException cause) {
            checkCancelled();
            LOG.log(Level.WARNING, "Cannot extract {0} ({1}): {2}", hit.name(), hit.uid(), cause.getMessage());
            logEvery(progress.updateAndGet(Progress::plusFailed));
        }

        private void logEvery(Progress current) {
            if ((current.extracted() + current.failed()) % LOG_EVERY == 0) {
                LOG.log(Level.INFO, "{0} ({1}): {2} signs extracted, {3} failed, {4} of {5} media downloaded",
                        current.category(), current.language().code(), current.extracted(), current.failed(),
                        current.mediaDownloaded(), current.mediaQueued());
            }
        }

        private void checkCancelled() {
            if (cancelled.get()) {
                throw new CancellationException("Synchronization cancelled");
            }
        }
    }
}
