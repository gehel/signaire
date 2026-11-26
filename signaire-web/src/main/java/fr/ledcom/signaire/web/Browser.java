package fr.ledcom.signaire.web;

import java.awt.AWTError;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.jcip.annotations.ThreadSafe;

/**
 * Opens the standalone application in the browser of the user once it is started ({@code
 * signaire.standalone.open-browser}). A bean of {@link StandaloneConfiguration}.
 */
@ThreadSafe
public class Browser {

    private static final Logger LOG = System.getLogger(Browser.class.getName());

    private volatile int port;

    @EventListener
    public void started(WebServerInitializedEvent event) {
        port = event.getWebServer().getPort();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        URI uri = URI.create("http://localhost:" + port + "/");
        LOG.log(Level.INFO, "Signaire is ready on {0}", uri);
        open(uri);
    }

    /** Opens a page in the browser of the user; says where it is when no browser can be opened. */
    static void open(URI uri) {
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
            Optional<List<String>> command = command(System.getProperty("os.name"), uri);
            if (command.isPresent()) {
                start(command.get());
                return;
            }
        } catch (IOException | UnsupportedOperationException | AWTError e) {
            // e.g. headless, or a DISPLAY without X server: Signaire works anyway
            LOG.log(Level.DEBUG, "Cannot open the browser", e);
        }
        LOG.log(Level.WARNING, "Open {0} in your browser", uri);
    }

    /** The command that opens a page in the browser of a system, when Java can't. */
    static Optional<List<String>> command(@Nullable String osName, URI uri) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return Optional.of(List.of("rundll32", "url.dll,FileProtocolHandler", uri.toString()));
        }
        if (os.startsWith("mac")) {
            return Optional.of(List.of("open", uri.toString()));
        }
        if (os.contains("linux") || os.contains("bsd")) {
            return Optional.of(List.of("xdg-open", uri.toString()));
        }
        return Optional.empty();
    }

    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "a fixed program, and a URI of localhost")
    private static void start(List<String> command) throws IOException {
        new ProcessBuilder(command).inheritIO().start();
    }
}
