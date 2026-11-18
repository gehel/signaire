package fr.ledcom.signaire.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class UserDirectoriesEnvironmentTest {

    @TempDir
    Path home;

    @Test
    void definesTheDefaultDirectoriesAndReadsTheUserConfiguration() {
        StandardEnvironment environment = new StandardEnvironment();
        new UserDirectoriesEnvironment(new UserDirectories(Map.of(), "Linux", home))
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("signaire.bundles.directory"))
                .isEqualTo(home.resolve(".local/share/signaire/bundles").toString());
        assertThat(environment.getProperty("signaire.media.cache"))
                .isEqualTo(home.resolve(".cache/signaire/media").toString());
        assertThat(environment.getProperty("signaire.index.directory"))
                .isEqualTo(home.resolve(".local/state/signaire/index").toString());
        assertThat(environment.getProperty("signaire.state.directory"))
                .isEqualTo(home.resolve(".local/state/signaire").toString());
        assertThat(environment.getProperty("signaire.config.directory"))
                .isEqualTo(home.resolve(".config/signaire").toString());
        assertThat(environment.getProperty("spring.config.additional-location"))
                .isEqualTo("optional:file:" + home.resolve(".config/signaire") + "/");
    }

    @Test
    void theApplicationSettingsWin() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("command line", Map.of(
                "signaire.bundles.directory", "/elsewhere", "spring.config.additional-location", "file:/etc/x/")));
        new UserDirectoriesEnvironment(new UserDirectories(Map.of(), "Linux", home))
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("signaire.bundles.directory")).isEqualTo("/elsewhere");
        assertThat(environment.getProperty("spring.config.additional-location")).isEqualTo("file:/etc/x/");
    }
}
