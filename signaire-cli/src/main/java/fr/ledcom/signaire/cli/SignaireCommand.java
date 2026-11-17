package fr.ledcom.signaire.cli;

import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.TypeConversionException;

/** Root command of the CLI; subcommands are registered as nested commands. */
@Component
@Command(name = "signaire", resourceBundle = "fr.ledcom.signaire.cli.messages",
        subcommands = ExtractCommand.class)
public class SignaireCommand implements Runnable {

    @SuppressFBWarnings(value = "FCBL_FIELD_COULD_BE_LOCAL", justification = "Set by picocli through reflection")
    @Option(names = {"-h", "--help"}, usageHelp = true, descriptionKey = "help")
    private boolean helpRequested;

    /**
     * Creates the command line for this command. Conversion errors are reported with the converter's
     * (localized) message only, without picocli's English prefix.
     */
    public static CommandLine commandLine(SignaireCommand command, IFactory factory) {
        CommandLine commandLine = new CommandLine(command, factory);
        commandLine.setParameterExceptionHandler((ParameterException e, String[] args) -> {
            CommandLine failing = e.getCommandLine();
            Throwable cause = e.getCause();
            failing.getErr().println(cause instanceof TypeConversionException ? cause.getMessage() : e.getMessage());
            failing.usage(failing.getErr());
            return failing.getCommandSpec().exitCodeOnInvalidInput();
        });
        return commandLine;
    }

    @Override
    public void run() {
        // Without a subcommand, there is nothing to do: picocli prints the usage when asked.
    }
}
