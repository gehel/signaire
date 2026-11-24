package fr.ledcom.signaire.web;

import java.time.Duration;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The theme of the pages: the system's (light or dark, {@code prefers-color-scheme}), unless the user chose one with
 * the {@code theme} parameter ({@code ?theme=dark}), remembered in a cookie; {@code auto} forgets the choice. The
 * pages give it to Pico CSS: {@code <html data-theme="dark">}.
 */
@ControllerAdvice
public class ThemeAdvice {

    static final String COOKIE = "signaire-theme";
    static final String PARAMETER = "theme";
    private static final String AUTO = "auto";
    private static final Set<String> THEMES = Set.of("light", "dark");
    /** How long the choice is remembered. */
    private static final Duration REMEMBERED = Duration.ofDays(5L * 365);

    /** The theme chosen by the user, null to follow the system. */
    @ModelAttribute("theme")
    public String theme(@RequestParam(name = PARAMETER, required = false) String choice,
            @CookieValue(name = COOKIE, required = false) String remembered, HttpServletResponse response) {
        if (AUTO.equals(choice)) {
            remember(response, "", Duration.ZERO);
            return null;
        }
        if (isTheme(choice)) {
            remember(response, choice, REMEMBERED);
            return choice;
        }
        return isTheme(remembered) ? remembered : null;
    }

    /** Sets of {@code Set.of} reject null. */
    private static boolean isTheme(String theme) {
        return theme != null && THEMES.contains(theme);
    }

    private static void remember(HttpServletResponse response, String theme, Duration duration) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, theme)
                .path("/").maxAge(duration).httpOnly(true).sameSite("Lax").build().toString());
    }
}
