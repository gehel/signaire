package fr.ledcom.signaire.cli;

import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import fr.ledcom.signaire.core.SignaireCoreConfiguration;
import picocli.CommandLine.IFactory;

@SpringBootApplication
@Import(SignaireCoreConfiguration.class)
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
        exitCode = SignaireCommand.commandLine(command, factory).execute(commandArguments(args));
    }

    /**
     * The arguments for picocli: without the Spring properties ({@code --signaire.index.directory=…}), which
     * Spring Boot has already read. No option of the commands has a dot in its name.
     */
    static String[] commandArguments(String... args) {
        return Arrays.stream(args).filter(arg -> !isSpringProperty(arg)).toArray(String[]::new);
    }

    /** Whether the argument is a Spring property: {@code --name.with.dots=value}. */
    private static boolean isSpringProperty(String arg) {
        int equals = arg.indexOf('=');
        return arg.startsWith("--") && equals > 2 && arg.substring(2, equals).contains(".");
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
