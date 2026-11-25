package fr.ledcom.signaire.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;

import fr.ledcom.signaire.extractor.ExtractionListener;
import fr.ledcom.signaire.extractor.ExtractionStatus;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignEnumerator;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * Follows the extraction of a {@link SignSuisseSync}: indexes the signs extracted, logs the status every 30 s, the
 * searches done and the failures.
 */
@ThreadSafe
final class SyncListener implements ExtractionListener {

    private static final Logger LOG = System.getLogger(SyncListener.class.getName());
    private static final Duration LOG_INTERVAL = Duration.ofSeconds(30);

    private final SyncIndexing indexing;
    @GuardedBy("this")
    private Instant lastLog = Instant.EPOCH;

    SyncListener(SyncIndexing indexing) {
        this.indexing = indexing;
    }

    @Override
    public void signExtracted(Sign sign) {
        indexing.add(sign);
    }

    @Override
    public synchronized void progress(ExtractionStatus status) {
        indexing.flushIfDue();
        Instant now = Instant.now();
        if (status.stage() == ExtractionStatus.Stage.DONE || !now.isBefore(lastLog.plus(LOG_INTERVAL))) {
            lastLog = now;
            LOG.log(Level.INFO, "{0}: {1}, {2} searches ({3} left), {4} signs found, {5} read, {6} reused, "
                    + "{7} failed, {8} of {9} media downloaded ({10} failed), {11} requests done, {12} left",
                    status.category(), status.stage(), status.searches().sent(), status.searches().pending(),
                    status.signs().found(), status.signs().read(), status.signs().reused(), status.signs().failed(),
                    status.media().downloaded(), status.media().queued(), status.media().failed(),
                    status.requestsDone(), status.requestsLeft());
        }
    }

    @Override
    public void enumerated(Category category, Language language, SignEnumerator.Result result) {
        LOG.log(Level.INFO, "{0} ({1}): {2} signs found in {3} searches", category, language.code(),
                result.hits().size(), result.queries());
    }

    @Override
    public void signFailed(SearchHit hit, RuntimeException cause) {
        LOG.log(Level.WARNING, "Cannot extract {0} ({1}): {2}", hit.name(), hit.uid(), cause.getMessage());
    }

    @Override
    public void mediaFailed(Sign sign, Media media, RuntimeException cause) {
        LOG.log(Level.WARNING, "Cannot download {0} of {1}: {2}", media.sourceUrl(), sign.name(),
                cause.getMessage());
    }
}
