package fr.ledcom.signaire.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import fr.ledcom.signaire.core.search.SearchException;

/** Explains to users that the search index is unavailable. */
@ControllerAdvice
public class SearchErrors {

    private static final Logger LOG = System.getLogger(SearchErrors.class.getName());

    private final Links links;

    public SearchErrors(Links links) {
        this.links = links;
    }

    @ExceptionHandler(SearchException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ModelAndView searchUnavailable(SearchException e) {
        LOG.log(Level.WARNING, "Search unavailable", e);
        // model attributes are not added to the views of exception handlers
        return new ModelAndView("error/search-unavailable", Links.ATTRIBUTE, links);
    }
}
