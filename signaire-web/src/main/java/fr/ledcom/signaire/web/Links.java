package fr.ledcom.signaire.web;

import java.nio.charset.StandardCharsets;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import fr.ledcom.signaire.model.Media;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Links to variants of the current page, for the templates: {@code ${links.withPage(2)}}. Thymeleaf doesn't
 * allow bean references ({@code @links}) in links, so this is a model attribute of every page.
 */
@ControllerAdvice
public class Links {

    static final String ATTRIBUTE = "links";

    @ModelAttribute(ATTRIBUTE)
    public Links links() {
        return this;
    }

    /** The current page in another UI language. */
    public String withLanguage(String language) {
        return current().replaceQueryParam(ThemeAdvice.PARAMETER)
                .replaceQueryParam(LocaleConfiguration.PARAMETER, language).build().toUriString();
    }

    /** The current page in another theme: {@code auto}, {@code light} or {@code dark}. */
    public String withTheme(String theme) {
        return current().replaceQueryParam(LocaleConfiguration.PARAMETER)
                .replaceQueryParam(ThemeAdvice.PARAMETER, theme).build().toUriString();
    }

    /** Another page of the current search. */
    public String withPage(int page) {
        return current().replaceQueryParam(LocaleConfiguration.PARAMETER).replaceQueryParam(ThemeAdvice.PARAMETER)
                .replaceQueryParam("page", page).build().toUriString();
    }

    /**
     * Address of a media: served from its bundle when it was downloaded, else from SignSuisse.
     */
    public String media(Media media) {
        if (media.file() == null) {
            return media.sourceUrl();
        }
        return request().getContextPath() + "/" + UriUtils.encodePath(media.file(), StandardCharsets.UTF_8);
    }

    private static UriComponentsBuilder current() {
        HttpServletRequest request = request();
        return UriComponentsBuilder.fromPath(request.getRequestURI()).query(request.getQueryString());
    }

    private static HttpServletRequest request() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
