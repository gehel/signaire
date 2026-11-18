package fr.ledcom.signaire.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import picocli.CommandLine;
import picocli.CommandLine.IFactory;

@SpringBootTest
class SignaireCommandTest {

    @Autowired
    private IFactory factory;

    @Autowired
    private SignaireCommand command;

    @Test
    void helpIsDisplayedInFrench() {
        StringWriter out = new StringWriter();
        CommandLine commandLine = SignaireCommand.commandLine(command, factory);
        commandLine.setOut(new PrintWriter(out));

        int exitCode = commandLine.execute("--help");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("Utilisation : signaire")
                .contains("Lexique de la langue des signes suisse")
                .contains("Affiche cette aide et quitte.")
                .contains("Commandes :")
                .contains("extraire");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            de | Verwendung: signaire | Lexikon der Schweizer Gebärdensprache | Befehle:
            it | Utilizzo: signaire   | Lessico della lingua dei segni svizzera | Comandi:
            en | Usage: signaire      | Swiss sign language lexicon            | Commands:
            """)
    void helpIsDisplayedInTheUserLanguage(String language, String usage, String description, String commands) {
        StringWriter out = new StringWriter();
        int exitCode;
        try (DefaultLocale locale = DefaultLocale.set(Locale.of(language))) {
            CommandLine commandLine = SignaireCommand.commandLine(command, factory);
            commandLine.setOut(new PrintWriter(out));
            exitCode = commandLine.execute("--help");
        }

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains(usage, description, commands, "extraire");
    }

    @Test
    void bundlesAreInTheConfiguredDirectoryByDefault() {
        StringWriter out = new StringWriter();
        CommandLine commandLine = SignaireCommand.commandLine(command, factory);
        commandLine.setOut(new PrintWriter(out));

        commandLine.execute("charger", "--help");

        assertThat(out.toString()).contains("(défaut : target/test-bundles)");
    }
}
