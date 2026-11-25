package fr.ledcom.signaire.web;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListException;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Sign;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** The page of a sign, from which logged in users add the sign to their lists. */
@Controller
public class SignController {

    private final SignSearchService search;
    private final WordListService lists;

    public SignController(SignSearchService search, WordListService lists) {
        this.search = search;
        this.lists = lists;
    }

    @GetMapping("/signs/{uid}")
    public ModelAndView sign(@PathVariable("uid") String uid,
            @AuthenticationPrincipal @Nullable AccountPrincipal user) {
        return search.findByUid(uid)
                .map(sign -> signPage("sign", sign, user, null))
                .orElseGet(SignController::notFound);
    }

    @PostMapping("/signs/{uid}/lists/{listId}")
    public ModelAndView addToList(@PathVariable("uid") String uid, @PathVariable("listId") long listId,
            @AuthenticationPrincipal AccountPrincipal user, HttpServletRequest request, HttpServletResponse response) {
        return changeLists(uid, user, request, response, () -> lists.add(user.accountId(), listId, uid));
    }

    @PostMapping("/signs/{uid}/lists/{listId}/remove")
    public ModelAndView removeFromList(@PathVariable("uid") String uid, @PathVariable("listId") long listId,
            @AuthenticationPrincipal AccountPrincipal user, HttpServletRequest request, HttpServletResponse response) {
        return changeLists(uid, user, request, response, () -> lists.remove(user.accountId(), listId, uid));
    }

    /** Creates a list with the sign. */
    @PostMapping("/signs/{uid}/lists")
    public ModelAndView addToNewList(@PathVariable("uid") String uid, @RequestParam("name") String name,
            @AuthenticationPrincipal AccountPrincipal user, HttpServletRequest request, HttpServletResponse response) {
        return changeLists(uid, user, request, response,
                () -> lists.add(user.accountId(), lists.create(user.accountId(), name).id(), uid));
    }

    private ModelAndView changeLists(String uid, AccountPrincipal user, HttpServletRequest request,
            HttpServletResponse response, Runnable change) {
        Sign sign = search.findByUid(uid).orElse(null);
        if (sign == null) {
            return notFound();
        }
        String error = null;
        try {
            change.run();
        } catch (WordListException e) {
            if (e.reason() == WordListException.Reason.NOT_FOUND) {
                throw e;
            }
            error = "lists.error." + e.reason().name();
        }
        if (Htmx.isPartialRequest(request, response)) {
            return signPage("sign :: lists", sign, user, error);
        }
        // the template takes the uid from the path variables of the request
        return error == null ? new ModelAndView(new RedirectView("/signs/{uid}", true))
                : signPage("sign", sign, user, error);
    }

    private ModelAndView signPage(String view, Sign sign, @Nullable AccountPrincipal user,
            @Nullable String listError) {
        ModelAndView page = new ModelAndView(view, "sign", sign);
        if (user != null) {
            List<WordList> userLists = lists.lists(user.accountId());
            Set<Long> withSign = new HashSet<>(lists.listsContaining(user.accountId(), sign.uid()));
            page.addObject("lists", userLists);
            page.addObject("listsWithSign", withSign);
            page.addObject("listError", listError);
            page.addObject("maxNameLength", WordListService.MAX_NAME_LENGTH);
        }
        return page;
    }

    private static ModelAndView notFound() {
        return new ModelAndView("error/sign-not-found", HttpStatus.NOT_FOUND);
    }
}
