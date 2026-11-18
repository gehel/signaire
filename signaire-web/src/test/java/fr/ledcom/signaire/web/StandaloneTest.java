package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandaloneTest {

    @TempDir
    Path config;

    @Test
    void serversNeedNoSetupAndHaveNoSingleUser() {
        Standalone server = new Standalone(false, true, false, config);

        assertThat(server.enabled()).isFalse();
        assertThat(server.needsSetup()).isFalse();
        assertThat(server.singleUser()).isFalse();
    }

    @Test
    void theFirstRunNeedsASetup() {
        Standalone standalone = new Standalone(true, false, false, config);

        assertThat(standalone.needsSetup()).isTrue();
    }

    @Test
    void theSetupIsKeptForTheNextRuns() throws IOException {
        Standalone standalone = new Standalone(true, false, false, config.resolve("signaire"));

        standalone.completeSetup(true, null);

        assertThat(standalone.needsSetup()).isFalse();
        assertThat(standalone.singleUser()).isTrue();
        Properties saved = load(config.resolve("signaire/standalone.properties"));
        assertThat(saved).containsEntry("signaire.standalone.setup-done", "true")
                .containsEntry("signaire.accounts.single-user", "true")
                .doesNotContainKey("signaire.accounts.admins");
    }

    @Test
    void severalUsersHaveAnAdministrator() throws IOException {
        Standalone standalone = new Standalone(true, false, false, config);

        standalone.completeSetup(false, "alice");

        assertThat(standalone.singleUser()).isFalse();
        assertThat(load(config.resolve("standalone.properties")))
                .containsEntry("signaire.accounts.single-user", "false")
                .containsEntry("signaire.accounts.admins", "alice");
    }

    private static Properties load(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        }
        return properties;
    }
}
