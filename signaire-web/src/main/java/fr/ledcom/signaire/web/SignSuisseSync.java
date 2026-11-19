package fr.ledcom.signaire.web;

import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
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
import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.extractor.ExtractionException;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/**
 * Synchronizes the bundles with SignSuisse, in the background: extracts categories into the bundle directory,
 * loading each bundle as soon as it is written. One synchronization at a time; it can be cancelled.
 */
@Component
public class SignSuisseSync {

    private static final Logger LOG = System.getLogger(SignSuisseSync.class.getName());

    private final SignSource source;
    private final BundleLoader loader;
    private final BundleLoading loading;
    private final Path bundles;
    private final Path mediaCache;
    private final AtomicReference<Progress> progress = new AtomicReference<>(Progress.IDLE);
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private Thread worker;

    public SignSuisseSync(SignSource source, BundleLoader loader, BundleLoading loading,
            @Value("${signaire.bundles.directory:bundles}") Path bundles,
            @Value("${signaire.media.cache:bundles/.media}") Path mediaCache) {
        this.source = source;
        this.loader = loader;
        this.loading = loading;
        this.bundles = bundles;
        this.mediaCache = mediaCache;
    }

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
            MediaChoice media) {
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
                .start(() -> run(selectedCategories, selectedLanguages, media));
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

    private void run(Set<Category> categories, Set<Language> languages, MediaChoice chosenMedia) {
        SyncExtraction extraction = new SyncExtraction(source, loader, bundles, mediaCache, progress, cancelled);
        try {
            for (Category category : categories) {
                extraction.extract(category, languages, chosenMedia);
            }
            progress.updateAndGet(p -> p.with(State.DONE, null));
        } catch (CancellationException _) {
            progress.updateAndGet(p -> p.with(State.CANCELLED, null));
        } catch (ExtractionException | BundleException | SearchException | UncheckedIOException e) {
            LOG.log(Level.ERROR, "SignSuisse synchronization failed", e);
            progress.updateAndGet(p -> p.with(State.FAILED, e.getMessage()));
        } finally {
            loading.loaded();
        }
    }
}
