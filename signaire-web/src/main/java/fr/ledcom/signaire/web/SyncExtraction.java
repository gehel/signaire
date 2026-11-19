package fr.ledcom.signaire.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.extractor.CategoryExtractor;
import fr.ledcom.signaire.extractor.ExtractionReport;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.extractor.SignEnumerator;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.extractor.SignSuisseClient;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.web.SignSuisseSync.CategoryResult;
import fr.ledcom.signaire.web.SignSuisseSync.Progress;

/** The work of a {@link SignSuisseSync}: extracts categories into bundles, and loads them. */
final class SyncExtraction {

    private static final Logger LOG = System.getLogger(SyncExtraction.class.getName());
    private static final int MAX_QUERY_LENGTH = 6;

    private final CategoryExtractor extractor;
    private final BundleLoader loader;
    private final Path bundles;
    private final Path mediaCache;
    private final AtomicReference<Progress> progress;

    SyncExtraction(SignSource source, BundleLoader loader, Path bundles, Path mediaCache,
            AtomicReference<Progress> progress, AtomicBoolean cancelled) {
        this.extractor = new CategoryExtractor(source,
                new SignEnumerator(source, SignSuisseClient.MAX_RESULTS, MAX_QUERY_LENGTH),
                new BundleWriter(Clock.systemUTC()), new SyncListener(progress, cancelled));
        this.loader = loader;
        this.bundles = bundles;
        this.mediaCache = mediaCache;
        this.progress = progress;
    }

    void extract(Category category, Set<Language> languages, MediaChoice media) {
        ExtractionReport report = extractor.extract(category, languages, bundles, mediaCache, media);
        loader.load(report.bundle());
        LOG.log(Level.INFO, "{0}: {1} signs written to {2} and loaded, {3} failed", category, report.signCount(),
                report.bundle(), report.failures().size());
        progress.updateAndGet(p -> p.plusDone(new CategoryResult(category, report.signCount(),
                report.failures().size(), report.enumerationComplete())));
    }
}
