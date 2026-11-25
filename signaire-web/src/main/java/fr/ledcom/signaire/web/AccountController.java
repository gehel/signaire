package fr.ledcom.signaire.web;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
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
    private final List<OidcProvider> oidcProviders;

    public AccountController(AccountService accounts,
            @Value("${signaire.accounts.registration-enabled:true}") boolean registrationEnabled,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.accounts = accounts;
        this.registrationEnabled = registrationEnabled;
        this.oidcProviders = oidcProviders(clientRegistrations.getIfAvailable());
    }

    /**
     * An OpenID Connect provider users can log in with.
     *
     * @param id the registration id, in the login URL
     * @param name the name shown to users
     */
    public record OidcProvider(String id, String name) {
    }

    private static List<OidcProvider> oidcProviders(@Nullable ClientRegistrationRepository registrations) {
        List<OidcProvider> providers = new ArrayList<>();
        // Spring Boot's repository lists its registrations; others can't be listed
        if (registrations instanceof Iterable<?> iterable) {
            for (Object registration : iterable) {
                if (registration instanceof ClientRegistration client) {
                    providers.add(new OidcProvider(client.getRegistrationId(), client.getClientName()));
                }
            }
        }
        return List.copyOf(providers);
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
    public String login(Model model) {
        model.addAttribute("oidcProviders", oidcProviders);
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
