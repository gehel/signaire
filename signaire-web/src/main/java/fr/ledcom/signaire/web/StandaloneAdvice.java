package fr.ledcom.signaire.web;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** The {@link Standalone} application, for the pages: no logout for the single user, a way to quit. */
@ControllerAdvice
public class StandaloneAdvice {

    private final ObjectProvider<Standalone> standalone;

    public StandaloneAdvice(ObjectProvider<Standalone> standalone) {
        this.standalone = standalone;
    }

    /** Null in the tests of parts of the web layer, and in the error pages of Spring Boot. */
    @ModelAttribute("standalone")
    public @Nullable Standalone standalone() {
        return standalone.getIfAvailable();
    }
}
