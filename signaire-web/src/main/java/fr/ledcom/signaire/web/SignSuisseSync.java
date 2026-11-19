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
import fr.ledcom.signaire.extractor.ExtractionOptions;
import fr.ledcom.signaire.extractor.ExtractionStatus;
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
    private final AtomicReference<SyncExtraction> extraction = new AtomicReference<>();
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
     * @param requests requests sent to SignSuisse
     */
    public record CategoryResult(Category category, int signs, int failures, boolean complete, int requests) {
    }

    /**
     * Where the synchronization is.
     *
     * @param current where the category being extracted is, or null
     * @param done the categories done
     * @param left the categories not started yet
     * @param error why the synchronization failed, or null
     */
    public record Progress(State state, ExtractionStatus current, List<CategoryResult> done, List<Category> left,
            String error) {

        static final Progress IDLE = new Progress(State.IDLE, null, List.of(), List.of(), null);

        public Progress {
            done = List.copyOf(done);
            left = List.copyOf(left);
        }

        static Progress started(Collection<Category> categories) {
            return new Progress(State.RUNNING, null, List.of(), List.copyOf(categories), null);
        }

        public boolean running() {
            return state == State.RUNNING;
        }

        Progress with(State newState, String newError) {
            return new Progress(newState, null, done, left, newError);
        }

        Progress withCurrent(ExtractionStatus status) {
            List<Category> newLeft = left.stream().filter(category -> category != status.category()).toList();
            return new Progress(state, status, done, newLeft, error);
        }

        Progress categoryDone(CategoryResult result) {
            List<CategoryResult> newDone = new ArrayList<>(done);
            newDone.add(result);
            List<Category> newLeft = left.stream().filter(category -> category != result.category()).toList();
            return new Progress(state, null, newDone, newLeft, error);
        }
    }

    public Progress progress() {
        Progress current = progress.get();
        SyncExtraction running = extraction.get();
        if (current.running() && running != null) {
            return running.status().map(current::withCurrent).orElse(current);
        }
        return current;
    }

    /**
     * Starts a synchronization, unless one is running.
     *
     * @return false when a synchronization is already running
     */
    public synchronized boolean start(Collection<Category> categories, Collection<Language> languages,
            ExtractionOptions options) {
        if (progress.get().running()) {
            return false;
        }
        Set<Category> selectedCategories = categories.isEmpty() ? EnumSet.allOf(Category.class)
                : EnumSet.copyOf(categories);
        Set<Language> selectedLanguages = languages.isEmpty() ? EnumSet.allOf(Language.class)
                : EnumSet.copyOf(languages);
        cancelled.set(false);
        progress.set(Progress.started(selectedCategories));
        SyncExtraction started = new SyncExtraction(source, loader, bundles, mediaCache, progress);
        extraction.set(started);
        worker = Thread.ofVirtual().name("signsuisse-sync")
                .start(() -> run(started, selectedCategories, selectedLanguages, options));
        return true;
    }

    /** Stops the synchronization; the categories done stay loaded. */
    public void cancel() {
        cancelled.set(true);
        SyncExtraction running = extraction.get();
        if (running != null) {
            running.cancel();
        }
    }

    /** Waits for the end of the synchronization; returns false on timeout. */
    public boolean awaitCompletion(Duration timeout) throws InterruptedException {
        Thread running;
        synchronized (this) {
            running = worker;
        }
        return running == null || running.join(timeout);
    }

    private void run(SyncExtraction work, Set<Category> categories, Set<Language> languages,
            ExtractionOptions options) {
        try {
            for (Category category : categories) {
                if (cancelled.get()) {
                    throw new CancellationException("Synchronization cancelled");
                }
                work.extract(category, languages, options);
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
