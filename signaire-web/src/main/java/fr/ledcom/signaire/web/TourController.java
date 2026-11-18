package fr.ledcom.signaire.web;

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The guided tour: new visitors are welcomed on the home page and offered a presentation of the features, step by
 * step. Finishing or dismissing it is remembered in a cookie.
 */
@Controller
@ControllerAdvice
public class TourController {

    static final String COOKIE = "signaire-tour";
    /** How long the end of the tour is remembered. */
    private static final Duration REMEMBERED = Duration.ofDays(5L * 365);
    /** The steps: the topic of the manual each presents, and a page to try it. */
    static final List<Step> STEPS = List.of(
            new Step("search", "/?q=lac"),
            new Step("signs", "/?q=lac"),
            new Step("lists", "/lists"),
            new Step("exports", "/"),
            new Step("requests", "/help"));

    /**
     * A step of the tour.
     *
     * @param topic the topic of the manual: name of its messages, and link to read more
     * @param tryIt where to try the feature
     */
    public record Step(String topic, String tryIt) {
    }

    /** Whether the page should welcome the visitor: not when the tour was done or dismissed. */
    @ModelAttribute("welcome")
    public boolean welcome(@CookieValue(name = COOKIE, required = false) String done) {
        return done == null;
    }

    @GetMapping("/tour/{number}")
    public String step(@PathVariable("number") int number, Model model) {
        if (number < 1 || number > STEPS.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("step", STEPS.get(number - 1));
        model.addAttribute("number", number);
        model.addAttribute("count", STEPS.size());
        return "tour";
    }

    /** The tour is done, or dismissed: the welcome is not shown again. */
    @GetMapping("/tour/done")
    public String done(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "done")
                .path("/").maxAge(REMEMBERED).httpOnly(true).sameSite("Lax").build().toString());
        return "redirect:/";
    }
}
