package fr.ledcom.signaire.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

class UserDirectoriesTest {

    private static final Path HOME = Path.of("/home/alice");

    @Test
    void followsTheXdgVariables() {
        UserDirectories directories = new UserDirectories(Map.of(
                "XDG_DATA_HOME", "/data", "XDG_CONFIG_HOME", "/config", "XDG_CACHE_HOME", "/cache",
                "XDG_STATE_HOME", "/state"), "Linux", HOME);

        assertThat(directories.data()).isEqualTo(Path.of("/data/signaire"));
        assertThat(directories.config()).isEqualTo(Path.of("/config/signaire"));
        assertThat(directories.cache()).isEqualTo(Path.of("/cache/signaire"));
        assertThat(directories.state()).isEqualTo(Path.of("/state/signaire"));
    }

    @Test
    void defaultsToTheXdgDirectoriesOfTheHome() {
        UserDirectories directories = new UserDirectories(Map.of(), "Linux", HOME);

        assertThat(directories.data()).isEqualTo(HOME.resolve(".local/share/signaire"));
        assertThat(directories.config()).isEqualTo(HOME.resolve(".config/signaire"));
        assertThat(directories.cache()).isEqualTo(HOME.resolve(".cache/signaire"));
        assertThat(directories.state()).isEqualTo(HOME.resolve(".local/state/signaire"));
    }

    @Test
    void ignoresRelativePathsAsTheSpecificationSays() {
        UserDirectories directories = new UserDirectories(Map.of("XDG_DATA_HOME", "data", "XDG_CONFIG_HOME", ""),
                "Linux", HOME);

        assertThat(directories.data()).isEqualTo(HOME.resolve(".local/share/signaire"));
        assertThat(directories.config()).isEqualTo(HOME.resolve(".config/signaire"));
    }

    @Test
    void usesTheUsualDirectoriesOfMacOs() {
        UserDirectories directories = new UserDirectories(Map.of(), "Mac OS X", HOME);

        assertThat(directories.data()).isEqualTo(HOME.resolve("Library/Application Support/Signaire"));
        assertThat(directories.config()).isEqualTo(HOME.resolve("Library/Application Support/Signaire"));
        assertThat(directories.cache()).isEqualTo(HOME.resolve("Library/Caches/Signaire"));
    }

    @Test
    void usesTheUsualDirectoriesOfWindows() {
        UserDirectories directories = new UserDirectories(Map.of("APPDATA", "C:\\Users\\alice\\AppData\\Roaming",
                "LOCALAPPDATA", "C:\\Users\\alice\\AppData\\Local"), "Windows 11", HOME);

        assertThat(directories.config().toString()).endsWith("Roaming" + java.io.File.separator + "Signaire");
        assertThat(directories.data().toString()).endsWith("Local" + java.io.File.separator + "Signaire");
        assertThat(directories.cache().toString()).contains("Local").endsWith("cache");
    }

    @Test
    void theXdgVariablesWinOnEverySystem() {
        assertThat(new UserDirectories(Map.of("XDG_DATA_HOME", "/data"), "Mac OS X", HOME).data())
                .isEqualTo(Path.of("/data/signaire"));
    }

    @Test
    void namesTheDefaultLocations() {
        UserDirectories directories = new UserDirectories(Map.of(), "Linux", HOME);

        assertThat(directories.bundles()).isEqualTo(HOME.resolve(".local/share/signaire/bundles"));
        assertThat(directories.mediaCache()).isEqualTo(HOME.resolve(".cache/signaire/media"));
        assertThat(directories.index()).isEqualTo(HOME.resolve(".local/state/signaire/index"));
        assertThat(directories.configFile()).isEqualTo(HOME.resolve(".config/signaire/application.properties"));
    }
}
