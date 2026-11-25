package fr.ledcom.signaire.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import fr.ledcom.signaire.accounts.AccountException;
import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.extractor.ExtractionOptions;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The first run of the standalone application: alone or with several accounts (the first one an administrator),
 * and the signs to download from SignSuisse. Only from this computer, and only once.
 */
@Controller
public class SetupController {

    private static final String HOME = "redirect:/";
    private static final String SETUP = "setup";

    private final Standalone standalone;
    private final AccountService accounts;
    private final AdminUsers admins;
    private final SignSuisseSync sync;

    public SetupController(Standalone standalone, AccountService accounts, AdminUsers admins, SignSuisseSync sync) {
        this.standalone = standalone;
        this.accounts = accounts;
        this.admins = admins;
        this.sync = sync;
    }

    /**
     * The choices of the setup.
     *
     * @param alone whether one person uses Signaire, without account (the default)
     * @param username the administrator, with several accounts
     * @param confirmation the password again
     * @param categories the categories to download now; none to download later
     * @param languages the sign languages to download, all when empty
     */
    public record Setup(Boolean alone, @Nullable String username, @Nullable String displayName,
            @Nullable String password, @Nullable String confirmation,
            List<Category> categories, List<Language> languages, MediaChoice media) {

        /** Unchecked boxes are not sent. */
        public Setup {
            alone = alone == null || alone;
            media = media == null ? MediaChoice.NONE : media;
            categories = categories == null ? List.of() : List.copyOf(categories);
            languages = languages == null ? List.of() : List.copyOf(languages);
        }
    }

    @ModelAttribute
    void choices(Model model) {
        model.addAttribute("allCategories", Arrays.asList(Category.values()));
        model.addAttribute("allLanguages", Arrays.asList(Language.values()));
        model.addAttribute("allMedia", Arrays.asList(MediaChoice.values()));
        model.addAttribute("minPasswordLength", AccountService.MIN_PASSWORD_LENGTH);
    }

    @GetMapping("/setup")
    public String form(HttpServletRequest request, Locale locale, Model model) {
        if (!allowed(request)) {
            return HOME;
        }
        model.addAttribute(SETUP, new Setup(Boolean.TRUE, "", "", "", "", List.of(), List.of(signLanguage(locale)),
                MediaChoice.NONE));
        return SETUP;
    }

    @PostMapping("/setup")
    public String complete(@ModelAttribute(SETUP) Setup setup, HttpServletRequest request, Model model) {
        if (!allowed(request)) {
            return HOME;
        }
        boolean alone = setup.alone();
        String admin = null;
        if (!alone) {
            admin = registerAdmin(setup, model);
            if (admin == null) {
                return SETUP;
            }
        }
        standalone.completeSetup(alone, admin);
        if (!setup.categories().isEmpty()) {
            sync.start(setup.categories(), setup.languages(), ExtractionOptions.of(setup.media()));
        }
        return next(setup);
    }

    /** Creates the administrator account; null, with the error in the model, when it can't be. */
    private @Nullable String registerAdmin(Setup setup, Model model) {
        String password = setup.password();
        if (password == null || !password.equals(setup.confirmation())) {
            model.addAttribute("error", "register.error.passwordMismatch");
            return null;
        }
        try {
            String admin = accounts.register(setup.username(), password, setup.displayName()).username();
            admins.add(admin);
            return admin;
        } catch (AccountException e) {
            model.addAttribute("error", "register.error." + e.reason().name());
            return null;
        }
    }

    /** With several accounts, the administrator logs in; alone, the download goes on in the administration. */
    private static String next(Setup setup) {
        boolean alone = setup.alone();
        if (!alone) {
            return "redirect:/login?registered";
        }
        return setup.categories().isEmpty() ? HOME : "redirect:/admin/sync";
    }

    /** Before the setup, from this computer; never on servers. */
    private boolean allowed(HttpServletRequest request) {
        if (!standalone.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!SingleUserFilter.isLocal(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return standalone.needsSetup();
    }

    /** The sign language of the language of the page, French by default. */
    private static Language signLanguage(Locale locale) {
        return switch (locale.getLanguage()) {
            case "de" -> Language.GERMAN;
            case "it" -> Language.ITALIAN;
            default -> Language.FRENCH;
        };
    }
}
