package fr.ledcom.signaire.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
 * usable, with the signs already indexed, while new or changed bundles are loaded. Elasticsearch often starts
 * along with the application, so loading is retried for a while when Elasticsearch doesn't answer.
 */
@Component
public class StartupLoading {

    private static final Logger LOG = System.getLogger(StartupLoading.class.getName());
    /** Elasticsearch takes about a minute to start. */
    private static final int DEFAULT_ATTEMPTS = 24;
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);

    private final BundleLoader loader;
    private final MediaLibrary media;
    private final SignEditor editor;
    private final Path directory;
    private final boolean enabled;
    private final int attempts;
    private final Duration retryDelay;
    private final Sleeper sleeper;

    /** Waits between attempts. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    @Autowired
    public StartupLoading(BundleLoader loader, MediaLibrary media, SignEditor editor,
            @Value("${signaire.bundles.directory:bundles}") Path directory,
            @Value("${signaire.bundles.load-on-startup:true}") boolean enabled) {
        this(loader, media, editor, directory, enabled, DEFAULT_ATTEMPTS, DEFAULT_RETRY_DELAY, Thread::sleep);
    }

    StartupLoading(BundleLoader loader, MediaLibrary media, SignEditor editor, Path directory, boolean enabled,
            int attempts, Duration retryDelay, Sleeper sleeper) {
        this.loader = loader;
        this.media = media;
        this.editor = editor;
        this.directory = directory;
        this.enabled = enabled;
        this.attempts = attempts;
        this.retryDelay = retryDelay;
        this.sleeper = sleeper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (enabled) {
            Thread.ofVirtual().name("bundle-loading").start(this::loadWhenReady);
        }
    }

    /** Loads the bundles, retrying while Elasticsearch doesn't answer. */
    public List<LoadReport> loadWhenReady() {
        for (int attempt = 1; attempt < attempts; attempt++) {
            try {
                return tryLoad();
            } catch (SearchException e) {
                LOG.log(Level.INFO, "Elasticsearch is not ready, loading the bundles again in {0}: {1}", retryDelay,
                        e.getMessage());
            }
            try {
                sleeper.sleep(retryDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return List.of();
            }
        }
        return load();
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
