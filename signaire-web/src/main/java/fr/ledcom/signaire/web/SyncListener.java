package fr.ledcom.signaire.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import fr.ledcom.signaire.extractor.ExtractionListener;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignEnumerator;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.web.SignSuisseSync.Progress;

/** Follows the extraction of a {@link SignSuisseSync} into its progress and the log, and stops it when cancelled. */
final class SyncListener implements ExtractionListener {

    private static final Logger LOG = System.getLogger(SyncListener.class.getName());
    /** Signs between two progress messages in the log. */
    private static final int LOG_EVERY = 25;

    private final AtomicReference<Progress> progress;
    private final AtomicBoolean cancelled;

    SyncListener(AtomicReference<Progress> progress, AtomicBoolean cancelled) {
        this.progress = progress;
        this.cancelled = cancelled;
    }

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
