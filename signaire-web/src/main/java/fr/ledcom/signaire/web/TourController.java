package fr.ledcom.signaire.web;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The guided tour: new visitors are welcomed on the home page and offered a presentation of the features, step by
 * step. A step is a dialog over the page it presents ({@code ?tour=2}), next to the element marked {@code
 * data-tour}; {@code js/tour.js} dims the rest of the page. Finishing or dismissing it is remembered in a cookie.
 */
@Controller
@ControllerAdvice
public class TourController {

    static final String COOKIE = "signaire-tour";
    /** How long the end of the tour is remembered. */
    private static final Duration REMEMBERED = Duration.ofDays(5L * 365);
    static final List<Step> STEPS = List.of(
            new Step("search", "/", "search"),
            new Step("signs", "/?q=lac", "results"),
            new Step("lists", "/?q=lac", "account"),
            new Step("exports", "/?q=lac", "export"),
            new Step("requests", "/?q=lac", "help"));

    /**
     * A step of the tour.
     *
     * @param topic the topic of the manual: name of its messages, and link to read more
     * @param page the page it is shown on
     * @param target the {@code data-tour} of the element it presents
     */
    public record Step(String topic, String page, String target) {
    }

    /**
     * The step shown on a page.
     *
     * @param number from 1
     */
    public record Current(Step step, int number, int count) {

        public @Nullable String previous() {
            return number > 1 ? href(number - 1) : null;
        }

        public @Nullable String next() {
            return number < count ? href(number + 1) : null;
        }
    }

    /** Whether the page should welcome the visitor: not when the tour was done or dismissed. */
    @ModelAttribute("welcome")
    public boolean welcome(@CookieValue(name = COOKIE, required = false) @Nullable String done) {
        return done == null;
    }

    /** The step of the tour to show over the page, if any. */
    @ModelAttribute("tour")
    public @Nullable Current tour(@RequestParam(name = "tour", required = false) @Nullable Integer number) {
        if (number == null || number < 1 || number > STEPS.size()) {
            return null;
        }
        return new Current(STEPS.get(number - 1), number, STEPS.size());
    }

    @GetMapping("/tour/{number}")
    public String step(@PathVariable("number") int number) {
        if (number < 1 || number > STEPS.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:" + href(number);
    }

    @GetMapping("/tour/done")
    public String done(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "done")
                .path("/").maxAge(REMEMBERED).httpOnly(true).sameSite("Lax").build().toString());
        return "redirect:/";
    }

    /** The page of a step, showing it. */
    static String href(int number) {
        String page = STEPS.get(number - 1).page();
        return page + (page.contains("?") ? "&" : "?") + "tour=" + number;
    }
}
