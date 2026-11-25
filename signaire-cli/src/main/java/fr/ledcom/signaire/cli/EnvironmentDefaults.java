package fr.ledcom.signaire.cli;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;

import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Defaults of options that come from the configuration: the bundle directory is the data directory of the user
 * (XDG), unless configured otherwise.
 */
final class EnvironmentDefaults implements IDefaultValueProvider {

    private final Environment environment;

    EnvironmentDefaults(Environment environment) {
        this.environment = environment;
    }

    @Override
    public @Nullable String defaultValue(ArgSpec argument) {
        if (argument instanceof OptionSpec option
                && ("--bundles".equals(option.longestName()) || "--sortie".equals(option.longestName()))) {
            return environment.getProperty("signaire.bundles.directory");
        }
        return null;
    }
}
