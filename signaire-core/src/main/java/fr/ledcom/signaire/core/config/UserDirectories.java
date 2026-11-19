package fr.ledcom.signaire.core.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where Signaire keeps the files of a user: the XDG base directories
 * (https://specifications.freedesktop.org/basedir/latest/) when their variables are set, else the usual
 * directories of the system (XDG defaults on Linux, Library on macOS, AppData on Windows).
 */
public final class UserDirectories {

    private static final String NAME = "signaire";
    private static final String CAPITALIZED_NAME = "Signaire";

    private final Map<String, String> environment;
    private final OperatingSystem system;
    private final Path home;

    private enum OperatingSystem {
        LINUX, MAC, WINDOWS
    }

    /**
     * @param osName the {@code os.name} system property
     */
    public UserDirectories(Map<String, String> environment, String osName, Path home) {
        this.environment = Map.copyOf(environment);
        this.system = operatingSystem(osName);
        this.home = home;
    }

    private static OperatingSystem operatingSystem(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return OperatingSystem.WINDOWS;
        }
        return os.startsWith("mac") ? OperatingSystem.MAC : OperatingSystem.LINUX;
    }

    /** The directories of the user running the JVM. */
    public static UserDirectories current() {
        return new UserDirectories(System.getenv(), System.getProperty("os.name"),
                Path.of(System.getProperty("user.home")));
    }

    /** Data that the user wants to keep: the bundles. */
    public Path data() {
        return xdg("XDG_DATA_HOME").orElseGet(() -> switch (system) {
            case MAC -> home.resolve("Library/Application Support").resolve(CAPITALIZED_NAME);
            case WINDOWS -> localAppData().resolve(CAPITALIZED_NAME);
            default -> home.resolve(".local/share").resolve(NAME);
        });
    }

    public Path config() {
        return xdg("XDG_CONFIG_HOME").orElseGet(() -> switch (system) {
            case MAC -> home.resolve("Library/Application Support").resolve(CAPITALIZED_NAME);
            case WINDOWS -> variable("APPDATA").map(Path::of).orElseGet(() -> home.resolve("AppData/Roaming"))
                    .resolve(CAPITALIZED_NAME);
            default -> home.resolve(".config").resolve(NAME);
        });
    }

    /** Files that can be downloaded again: the media cache of extractions. */
    public Path cache() {
        return xdg("XDG_CACHE_HOME").orElseGet(() -> switch (system) {
            case MAC -> home.resolve("Library/Caches").resolve(CAPITALIZED_NAME);
            case WINDOWS -> localAppData().resolve(CAPITALIZED_NAME).resolve("cache");
            default -> home.resolve(".cache").resolve(NAME);
        });
    }

    /** State to keep between runs, but not worth backing up: indexes, databases of standalone use. */
    public Path state() {
        return xdg("XDG_STATE_HOME").orElseGet(() -> switch (system) {
            case MAC -> home.resolve("Library/Application Support").resolve(CAPITALIZED_NAME);
            case WINDOWS -> localAppData().resolve(CAPITALIZED_NAME);
            default -> home.resolve(".local/state").resolve(NAME);
        });
    }

    /** The default directory of the bundles. */
    public Path bundles() {
        return data().resolve("bundles");
    }

    /** Media downloaded by extractions, shared by all bundles. */
    public Path mediaCache() {
        return cache().resolve("media");
    }

    /** The search index of the signs, rebuilt from the bundles when needed. */
    public Path index() {
        return state().resolve("index");
    }

    /** The configuration file of the user, read by the command line and the web application. */
    public Path configFile() {
        return config().resolve("application.properties");
    }

    /** An XDG directory, ignored unless absolute (as the specification says), with the Signaire directory. */
    private Optional<Path> xdg(String name) {
        return variable(name).map(Path::of).filter(Path::isAbsolute).map(path -> path.resolve(NAME));
    }

    private Path localAppData() {
        return variable("LOCALAPPDATA").map(Path::of).orElseGet(() -> home.resolve("AppData/Local"));
    }

    private Optional<String> variable(String name) {
        return Optional.ofNullable(environment.get(name)).filter(value -> !value.isBlank());
    }
}
