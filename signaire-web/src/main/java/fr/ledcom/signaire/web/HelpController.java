package fr.ledcom.signaire.web;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** The manual, also linked from the features it explains. */
@Controller
public class HelpController {

    private final HelpPages pages;

    public HelpController(HelpPages pages) {
        this.pages = pages;
    }

    @GetMapping("/help")
    public String manual(Locale locale, Model model) {
        model.addAttribute("pages", pages.all(locale));
        return "help/index";
    }

    @GetMapping("/help/{topic}")
    public String topic(@PathVariable("topic") String topic, Locale locale, Model model) {
        model.addAttribute("page", pages.page(topic, locale)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        model.addAttribute("pages", pages.all(locale));
        return "help/topic";
    }
}
