package fr.ledcom.signaire.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
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
        CommandLine commandLine = new CommandLine(command, factory);
        commandLine.setOut(new PrintWriter(out));

        int exitCode = commandLine.execute("--help");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("Utilisation : signaire")
                .contains("Lexique de la langue des signes suisse")
                .contains("Affiche cette aide et quitte.");
    }
}
