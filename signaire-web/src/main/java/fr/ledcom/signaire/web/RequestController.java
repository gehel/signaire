package fr.ledcom.signaire.web;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import fr.ledcom.signaire.accounts.SignRequestService;
import fr.ledcom.signaire.model.Language;

/** Users ask for missing signs, and follow their requests. */
@Controller
public class RequestController {

    private final SignRequestService requests;
    private final MessageSource messages;

    public RequestController(SignRequestService requests, MessageSource messages) {
        this.requests = requests;
        this.messages = messages;
    }

    @GetMapping("/requests")
    public String requests(@RequestParam(name = "word", required = false) String word,
            @AuthenticationPrincipal AccountPrincipal user, Model model) {
        addModel(user, model, word, null);
        return "requests";
    }

    @PostMapping("/requests")
    public String request(@RequestParam("word") String word, @RequestParam("language") Language language,
            @RequestParam(name = "comment", required = false) String comment,
            @AuthenticationPrincipal AccountPrincipal user, Locale locale, Model model, RedirectAttributes redirect) {
        try {
            String requested = requests.request(user.accountId(), word, language, comment).word();
            redirect.addFlashAttribute("status", messages.getMessage("requests.sent", new Object[] {requested},
                    locale));
            return "redirect:/requests";
        } catch (IllegalArgumentException _) {
            addModel(user, model, word, "requests.error.word");
            return "requests";
        }
    }

    private void addModel(AccountPrincipal user, Model model, String word, String error) {
        model.addAttribute("requests", requests.byRequester(user.accountId()));
        model.addAttribute("word", word == null ? "" : word);
        model.addAttribute("allLanguages", Arrays.asList(Language.values()));
        model.addAttribute("error", error);
    }
}
