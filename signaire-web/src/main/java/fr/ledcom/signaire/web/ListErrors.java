package fr.ledcom.signaire.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import fr.ledcom.signaire.accounts.WordListException;

/** Lists that don't exist, or belong to someone else, are not found. */
@ControllerAdvice
public class ListErrors {

    private final Links links;

    public ListErrors(Links links) {
        this.links = links;
    }

    @ExceptionHandler(WordListException.class)
    public ModelAndView listNotFound(WordListException e) {
        // model attributes are not added to the views of exception handlers
        ModelAndView page = new ModelAndView("error/list-not-found", HttpStatus.NOT_FOUND);
        page.addObject(Links.ATTRIBUTE, links);
        return page;
    }
}
