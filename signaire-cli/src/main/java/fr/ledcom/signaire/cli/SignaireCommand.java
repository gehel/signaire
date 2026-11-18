package fr.ledcom.signaire.cli;

import java.util.List;

import org.springframework.stereotype.Component;

import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.extractor.ExtractionException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IFactory;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.TypeConversionException;

/** Root command of the CLI; subcommands are registered as nested commands. */
@Component
@Command(name = "signaire", resourceBundle = "fr.ledcom.signaire.cli.messages",
        subcommands = {ExtractCommand.class, LoadCommand.class, SearchCommand.class})
public class SignaireCommand implements Runnable {

    private static final Messages MESSAGES = Messages.of("fr.ledcom.signaire.cli.messages");
    /** Expected failures, reported with a message rather than a stack trace. */
    private static final List<ExpectedError> EXPECTED_ERRORS = List.of(
            new ExpectedError(SearchException.class, "error.search"),
            new ExpectedError(BundleException.class, "error.bundle"),
            new ExpectedError(ExtractionException.class, "error.extraction"));

    /** A type of expected failure, and the key of its message. */
    private record ExpectedError(Class<? extends Exception> type, String key) {
    }

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
        commandLine.setExecutionExceptionHandler((Exception e, CommandLine failing, ParseResult parseResult) -> {
            String key = errorKey(e);
            if (key == null) {
                throw e;
            }
            String cause = e.getCause() == null ? "" : " (" + e.getCause().getMessage() + ")";
            failing.getErr().println(MESSAGES.get(key, e.getMessage() + cause));
            return 1;
        });
        return commandLine;
    }

    /** Key of the message for the expected failures, {@code null} for bugs. */
    private static String errorKey(Exception e) {
        return EXPECTED_ERRORS.stream()
                .filter(error -> error.type().isInstance(e))
                .map(ExpectedError::key)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void run() {
        // Without a subcommand, there is nothing to do: picocli prints the usage when asked.
    }
}
