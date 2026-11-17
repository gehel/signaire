package fr.ledcom.signaire.cli;

import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Root command of the CLI; subcommands are registered as nested commands. */
@Component
@Command(name = "signaire", resourceBundle = "fr.ledcom.signaire.cli.messages")
public class SignaireCommand implements Runnable {

    @SuppressFBWarnings(value = "FCBL_FIELD_COULD_BE_LOCAL", justification = "Set by picocli through reflection")
    @Option(names = {"-h", "--help"}, usageHelp = true, descriptionKey = "help")
    private boolean helpRequested;

    @Override
    public void run() {
        // Without a subcommand, there is nothing to do: picocli prints the usage when asked.
    }
}
