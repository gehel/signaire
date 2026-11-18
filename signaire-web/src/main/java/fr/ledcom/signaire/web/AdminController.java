package fr.ledcom.signaire.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import fr.ledcom.signaire.accounts.SignOverrideService;
import fr.ledcom.signaire.accounts.SignRequestService;

/** The home page of the administration. */
@Controller
public class AdminController {

    private final SignOverrideService overrides;
    private final SignRequestService requests;

    public AdminController(SignOverrideService overrides, SignRequestService requests) {
        this.overrides = overrides;
        this.requests = requests;
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("changedSigns", overrides.all().size());
        model.addAttribute("openRequests", requests.openCount());
        return "admin/index";
    }
}
