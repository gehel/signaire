package fr.ledcom.signaire.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stops the standalone application, which may have no console to stop it from: started with a double click.
 * Administrators only (the single user is one); never on servers.
 */
@Controller
public class QuitController {

    /** Long enough to send the page that says goodbye. */
    private static final Duration DELAY = Duration.ofMillis(500);

    private final Standalone standalone;
    private final Runnable stop;

    @Autowired
    public QuitController(Standalone standalone, ConfigurableApplicationContext context) {
        this(standalone, () -> SpringApplication.exit(context));
    }

    QuitController(Standalone standalone, Runnable stop) {
        this.standalone = standalone;
        this.stop = stop;
    }

    @PostMapping("/admin/quit")
    public String quit() {
        if (!standalone.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Thread.ofVirtual().name("quit").start(() -> {
            try {
                Thread.sleep(DELAY);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            stop.run();
        });
        return "quit";
    }
}
