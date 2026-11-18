package fr.ledcom.signaire.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import fr.ledcom.signaire.core.search.SignSearchService;

/** The page of a sign. */
@Controller
public class SignController {

    private final SignSearchService search;

    public SignController(SignSearchService search) {
        this.search = search;
    }

    @GetMapping("/signs/{uid}")
    public ModelAndView sign(@PathVariable("uid") String uid) {
        return search.findByUid(uid)
                .map(sign -> new ModelAndView("sign", "sign", sign))
                .orElseGet(() -> new ModelAndView("error/sign-not-found", HttpStatus.NOT_FOUND));
    }
}
