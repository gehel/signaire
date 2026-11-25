package fr.ledcom.signaire.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.jspecify.annotations.Nullable;

import net.jcip.annotations.ThreadSafe;

/**
 * The standalone application ({@link StandaloneApplication}): one person runs Signaire on their computer. It is set
 * up on its first run, alone (no account at all) or with several accounts; the choices are kept in
 * {@code standalone.properties}, in the configuration directory of the user, read at startup. A bean of
 * {@link SecurityConfiguration}.
 */
@ThreadSafe
public class Standalone {

    static final String SETTINGS = "standalone.properties";

    private final boolean enabled;
    private final Path settingsFile;
    private volatile boolean singleUser;
    private volatile boolean setupDone;

    public Standalone(boolean enabled, boolean singleUser, boolean setupDone, Path configDirectory) {
        this.enabled = enabled;
        this.singleUser = singleUser;
        this.setupDone = setupDone;
        this.settingsFile = configDirectory.resolve(SETTINGS);
    }

    /** Whether this is the standalone application, rather than a server. */
    public boolean enabled() {
        return enabled;
    }

    /** Whether every local visitor is the single user, an administrator, without logging in. */
    public boolean singleUser() {
        return enabled && singleUser;
    }

    /** Whether the standalone application runs for the first time, and must be set up. */
    public boolean needsSetup() {
        return enabled && !setupDone;
    }

    /**
     * Ends the setup, and keeps its choices for the next runs.
     *
     * @param admin the administrator, with several users
     */
    public synchronized void completeSetup(boolean alone, @Nullable String admin) {
        Properties properties = new Properties();
        properties.setProperty("signaire.standalone.setup-done", "true");
        properties.setProperty("signaire.accounts.single-user", Boolean.toString(alone));
        if (!alone && admin != null) {
            properties.setProperty("signaire.accounts.admins", admin);
        }
        try {
            Path directory = settingsFile.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            try (OutputStream out = Files.newOutputStream(settingsFile)) {
                properties.store(out, "Set up by Signaire on its first run");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save the setup in " + settingsFile, e);
        }
        this.singleUser = alone;
        this.setupDone = true;
    }
}
