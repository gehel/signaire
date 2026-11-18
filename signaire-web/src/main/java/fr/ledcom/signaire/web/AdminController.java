package fr.ledcom.signaire.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import fr.ledcom.signaire.accounts.SignOverrideService;

/** The home page of the administration. */
@Controller
public class AdminController {

    private final SignOverrideService overrides;

    public AdminController(SignOverrideService overrides) {
        this.overrides = overrides;
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("changedSigns", overrides.all().size());
        return "admin/index";
    }
}
