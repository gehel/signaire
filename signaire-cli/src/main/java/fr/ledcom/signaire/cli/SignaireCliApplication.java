package fr.ledcom.signaire.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import picocli.CommandLine.IFactory;

@SpringBootApplication
public class SignaireCliApplication implements CommandLineRunner, ExitCodeGenerator {

    private final IFactory factory;
    private final SignaireCommand command;
    private int exitCode;

    public SignaireCliApplication(IFactory factory, SignaireCommand command) {
        this.factory = factory;
        this.command = command;
    }

    public static void main(String... args) {
        System.exit(SpringApplication.exit(SpringApplication.run(SignaireCliApplication.class, args)));
    }

    @Override
    public void run(String... args) {
        exitCode = SignaireCommand.commandLine(command, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
