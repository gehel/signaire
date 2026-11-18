package fr.ledcom.signaire.core.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Applies the {@link UserDirectories} to Spring applications: the bundles, the media cache and the search index
 * default to the user's directories, and the user's configuration file is read too (after the application's own
 * {@code application.properties}, before the command line). Settings of the application always win.
 */
public class UserDirectoriesEnvironment implements EnvironmentPostProcessor, Ordered {

    static final String BUNDLES = "signaire.bundles.directory";
    static final String MEDIA_CACHE = "signaire.media.cache";
    static final String INDEX = "signaire.index.directory";
    private static final String ADDITIONAL_LOCATION = "spring.config.additional-location";

    private final UserDirectories directories;

    public UserDirectoriesEnvironment() {
        this(UserDirectories.current());
    }

    UserDirectoriesEnvironment(UserDirectories directories) {
        this.directories = directories;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put(BUNDLES, directories.bundles().toString());
        defaults.put(MEDIA_CACHE, directories.mediaCache().toString());
        defaults.put(INDEX, directories.index().toString());
        if (!environment.containsProperty(ADDITIONAL_LOCATION)) {
            defaults.put(ADDITIONAL_LOCATION, "optional:file:" + directories.config() + "/");
        }
        // the default properties of Spring Boot come after all the others, configuration files included
        DefaultPropertiesPropertySource.addOrMerge(defaults, environment.getPropertySources());
    }

    /** Before the configuration files are read, so that the user's file is one of them. */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }
}
