package fr.ledcom.signaire.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import fr.ledcom.signaire.core.SignaireCoreConfiguration;

@SpringBootApplication
@Import(SignaireCoreConfiguration.class)
public class SignaireWebApplication {

    protected SignaireWebApplication() {
        // instantiated by Spring only
    }

    public static void main(String... args) {
        SpringApplication.run(SignaireWebApplication.class, args);
    }
}
