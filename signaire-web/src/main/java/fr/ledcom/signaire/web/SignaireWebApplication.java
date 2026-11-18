package fr.ledcom.signaire.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SignaireWebApplication {

    protected SignaireWebApplication() {
        // instantiated by Spring only
    }

    public static void main(String... args) {
        SpringApplication.run(SignaireWebApplication.class, args);
    }
}
