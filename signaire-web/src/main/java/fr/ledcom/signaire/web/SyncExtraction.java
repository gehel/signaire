package fr.ledcom.signaire.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.extractor.CategoryExtractor;
import fr.ledcom.signaire.extractor.ExtractionOptions;
import fr.ledcom.signaire.extractor.ExtractionReport;
import fr.ledcom.signaire.extractor.ExtractionStatus;
import fr.ledcom.signaire.extractor.SignEnumerator;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.web.SignSuisseSync.CategoryResult;
import fr.ledcom.signaire.web.SignSuisseSync.Progress;

/** The work of a {@link SignSuisseSync}: extracts categories into bundles, and loads them. */
final class SyncExtraction {

    private static final Logger LOG = System.getLogger(SyncExtraction.class.getName());
    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(5);
    private static final int INDEX_BATCH = 25;
    private static final Duration INDEX_DELAY = Duration.ofSeconds(2);

    private final CategoryExtractor extractor;
    private final SyncIndexing indexing;
    private final BundleLoading loading;
    private final Path bundles;
    private final Path mediaCache;
    private final AtomicReference<Progress> progress;

    SyncExtraction(SignSource source, BundleLoading loading, Path bundles, Path mediaCache,
            AtomicReference<Progress> progress) {
        this.indexing = new SyncIndexing(loading, Clock.systemUTC(), INDEX_BATCH, INDEX_DELAY);
        this.extractor = new CategoryExtractor(source,
                SignEnumerator.forSignSuisse(source),
                new BundleWriter(Clock.systemUTC()), new SyncListener(indexing), PROGRESS_INTERVAL,
                Clock.systemUTC());
        this.loading = loading;
        this.bundles = bundles;
        this.mediaCache = mediaCache;
        this.progress = progress;
    }

    void extract(Category category, Set<Language> languages, ExtractionOptions options) {
        indexing.reset();
        ExtractionReport report = extractor.extract(category, languages, bundles, mediaCache, options);
        // the bundle has all the signs, with their media
        indexing.discard();
        loading.load(report.bundle());
        ExtractionStatus status = report.status();
        LOG.log(Level.INFO, "{0}: {1} signs written to {2} and loaded, {3} failed, in {4} requests", category,
                report.signCount(), report.bundle(), report.failures().size(), status.requestsDone());
        progress.updateAndGet(p -> p.categoryDone(new CategoryResult(category, report.signCount(),
                report.failures().size(), report.enumerationComplete(), status.requestsDone())));
    }

    /** Signs of the category being extracted already indexed. */
    int indexed() {
        return indexing.indexed();
    }

    /** Where the category being extracted is, while it is. */
    Optional<ExtractionStatus> status() {
        return extractor.status().filter(status -> status.stage() != ExtractionStatus.Stage.DONE);
    }

    void cancel() {
        extractor.cancel();
    }
}
