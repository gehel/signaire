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

/**
 * Loads the bundles of the configured directory when the application starts, in the background: the UI is
 * usable, with the signs already indexed, while new or changed bundles are loaded.
 */
@Component
public class StartupLoading {

    private static final Logger LOG = System.getLogger(StartupLoading.class.getName());

    private final BundleLoader loader;
    private final MediaLibrary media;
    private final SignEditor editor;
    private final Path directory;
    private final boolean enabled;

    public StartupLoading(BundleLoader loader, MediaLibrary media, SignEditor editor,
            @Value("${signaire.bundles.directory:bundles}") Path directory,
            @Value("${signaire.bundles.load-on-startup:true}") boolean enabled) {
        this.loader = loader;
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

    private List<LoadReport> tryLoad() {
        if (!Files.isDirectory(directory)) {
            LOG.log(Level.WARNING, "No bundle directory {0}: run \"signaire extraire\" to create bundles",
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
        media.refresh();
        // the bundles replace the signs changed by administrators
        editor.reapply();
        return reports;
    }
}
