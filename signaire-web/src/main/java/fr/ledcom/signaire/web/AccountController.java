package fr.ledcom.signaire.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import fr.ledcom.signaire.accounts.AccountException;
import fr.ledcom.signaire.accounts.AccountService;

/** Login and registration pages; Spring Security handles the login and logout requests themselves. */
@Controller
public class AccountController {

    private final AccountService accounts;
    private final boolean registrationEnabled;

    public AccountController(AccountService accounts,
            @Value("${signaire.accounts.registration-enabled:true}") boolean registrationEnabled) {
        this.accounts = accounts;
        this.registrationEnabled = registrationEnabled;
    }

    /**
     * What a user fills in to create an account.
     *
     * @param confirmation the password again
     */
    public record Registration(String username, String displayName, String password, String confirmation) {
    }

    @ModelAttribute("registrationEnabled")
    public boolean registrationEnabled() {
        return registrationEnabled;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registrationForm(Model model) {
        if (!registrationEnabled) {
            return "redirect:/login";
        }
        model.addAttribute("registration", new Registration("", "", "", ""));
        model.addAttribute("minPasswordLength", AccountService.MIN_PASSWORD_LENGTH);
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("registration") Registration registration, Model model) {
        if (!registrationEnabled) {
            return "redirect:/login";
        }
        model.addAttribute("minPasswordLength", AccountService.MIN_PASSWORD_LENGTH);
        if (registration.password() == null || !registration.password().equals(registration.confirmation())) {
            model.addAttribute("error", "register.error.passwordMismatch");
            return "register";
        }
        try {
            accounts.register(registration.username(), registration.password(), registration.displayName());
        } catch (AccountException e) {
            model.addAttribute("error", "register.error." + e.reason().name());
            return "register";
        }
        return "redirect:/login?registered";
    }
}
