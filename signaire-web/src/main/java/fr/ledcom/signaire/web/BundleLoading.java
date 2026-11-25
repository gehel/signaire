package fr.ledcom.signaire.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.bundles.LoadReport;
import fr.ledcom.signaire.core.media.MediaLibrary;
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/**
 * The loading of bundles in the web application: whatever loads bundles goes through here, so that the media are
 * refreshed and the changes of administrators applied again after each loading (the bundles replace the signs they
 * contain). The bundles of the configured directory are loaded when the application starts, in the background: the
 * UI is usable, with the signs already indexed, while new or changed bundles are loaded.
 */
@Component
public class BundleLoading {

    private static final Logger LOG = System.getLogger(BundleLoading.class.getName());

    private final BundleLoader loader;
    private final SignIndex index;
    private final MediaLibrary media;
    private final SignEditor editor;
    private final Path directory;
    private final boolean enabled;

    public BundleLoading(BundleLoader loader, SignIndex index, MediaLibrary media, SignEditor editor,
            @Value("${signaire.bundles.directory:bundles}") Path directory,
            @Value("${signaire.bundles.load-on-startup:true}") boolean enabled) {
        this.loader = loader;
        this.index = index;
        this.media = media;
        this.editor = editor;
        this.directory = directory;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (enabled) {
            Thread.ofVirtual().name("bundle-loading").start(this::load);
        }
    }

    /** Loads the bundles of the directory, applies the changes of administrators, reports in the logs. */
    public List<LoadReport> load() {
        try {
            return tryLoad();
        } catch (SearchException | BundleException e) {
            LOG.log(Level.ERROR, "Cannot load the bundles of " + directory, e);
            return List.of();
        }
    }

    /**
     * Loads one bundle (an upload), then refreshes the media and applies the changes of administrators again.
     *
     * @throws BundleException when the bundle is invalid
     */
    public LoadReport load(Path bundle) {
        LoadReport report = loader.load(bundle);
        loaded();
        return report;
    }

    /** Empties the index and loads all the bundles again: after a change of the index mapping. */
    public List<LoadReport> reindex() {
        index.recreate();
        return load();
    }

    /**
     * Indexes signs being extracted, before their bundle is written: with their media on SignSuisse (the media
     * library serves only the media of bundles) and the changes of administrators. Loading the bundle replaces them.
     */
    public void indexExtracted(List<Sign> signs) {
        editor.indexWithChanges(signs.stream().map(BundleLoading::linked).toList());
    }

    private static Example linked(Example example) {
        Media video = example.video();
        return video == null ? example : new Example(example.text(), linked(video));
    }

    private static Sign linked(Sign sign) {
        return sign.toBuilder()
                .videos(sign.videos().stream().map(BundleLoading::linked).toList())
                .examples(sign.examples().stream().map(BundleLoading::linked).toList())
                .dialectVariants(sign.dialectVariants().stream()
                        .map(variant -> new DialectVariant(variant.dialects(), linked(variant.video())))
                        .toList())
                .images(sign.images().stream().map(BundleLoading::linked).toList())
                .build();
    }

    private static Media linked(Media media) {
        return media.file() == null ? media : new Media(media.sourceUrl(), null);
    }

    /** After bundles were loaded by {@link BundleLoader} directly. */
    public void loaded() {
        media.refresh();
        // the bundles replace the signs changed by administrators
        editor.reapply();
    }

    private List<LoadReport> tryLoad() {
        if (!Files.isDirectory(directory)) {
            LOG.log(Level.WARNING, "No bundle directory {0} yet: download signs with the synchronization of the "
                    + "administration, or \"signaire extraire\"",
                    directory.toAbsolutePath());
            editor.reapply();
            return List.of();
        }
        List<LoadReport> reports = loader.loadDirectory(directory);
        for (LoadReport report : reports) {
            LOG.log(report.status() == LoadReport.Status.FAILED ? Level.WARNING : Level.INFO,
                    "Bundle {0}: {1} ({2} signs){3}", report.file().getFileName(), report.status(),
                    report.signCount(), report.error() == null ? "" : ", " + report.error());
        }
        loaded();
        return reports;
    }
}
