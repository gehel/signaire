package fr.ledcom.signaire.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

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
        return current().replaceQueryParam(LocaleConfiguration.PARAMETER, language).build().toUriString();
    }

    /** Another page of the current search. */
    public String withPage(int page) {
        return current().replaceQueryParam(LocaleConfiguration.PARAMETER).replaceQueryParam("page", page).build()
                .toUriString();
    }

    private static UriComponentsBuilder current() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        return UriComponentsBuilder.fromPath(request.getRequestURI()).query(request.getQueryString());
    }
}
