package fr.ledcom.signaire.web;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.MessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListException;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds signs of the search page to a list: the selected ones, or all the results of the search (up to
 * {@link SearchController#MAX_ADDED}). The list is an existing one, or a new one.
 */
@Controller
public class SearchToListController {

    private final SignSearchService search;
    private final WordListService lists;
    private final MessageSource messages;

    public SearchToListController(SignSearchService search, WordListService lists, MessageSource messages) {
        this.search = search;
        this.lists = lists;
        this.messages = messages;
    }

    @PostMapping("/lists/signs")
    public String add(@RequestParam(name = "scope", defaultValue = "selection") String scope,
            @RequestParam(name = "uid", required = false) List<String> uids,
            @RequestParam(name = "list", required = false) Long listId,
            @RequestParam(name = "newList", required = false) String newList,
            @RequestParam(name = "q", required = false) String text,
            @RequestParam(name = "language", required = false) Set<Language> languages,
            @RequestParam(name = "category", required = false) Set<Category> categories,
            @AuthenticationPrincipal AccountPrincipal user, Locale locale,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect, Model model) {
        List<String> signs;
        if ("all".equals(scope)) {
            signs = search.findUids(SearchController.query(text, languages, categories, 0, 1),
                    SearchController.MAX_ADDED);
        } else {
            signs = uids == null ? List.of() : uids;
        }
        String status;
        boolean error = true;
        if (signs.isEmpty()) {
            status = messages.getMessage("search.addTo.noSelection", null, locale);
        } else {
            try {
                WordList list = listId == null ? lists.create(user.accountId(), newList)
                        : lists.get(user.accountId(), listId);
                int added = lists.addAll(user.accountId(), list.id(), signs);
                status = messages.getMessage("search.addTo.added", new Object[] {added, list.name()}, locale);
                error = false;
            } catch (WordListException e) {
                if (e.reason() == WordListException.Reason.NOT_FOUND) {
                    throw e;
                }
                status = messages.getMessage("lists.error." + e.reason().name(),
                        new Object[] {WordListService.MAX_NAME_LENGTH}, locale);
            }
        }
        if (Htmx.isPartialRequest(request, response)) {
            model.addAttribute("status", status);
            model.addAttribute("statusError", error);
            return "search :: add-status";
        }
        redirect.addFlashAttribute("status", status);
        redirect.addFlashAttribute("statusError", error);
        return "redirect:" + searchUrl(text, languages, categories);
    }

    private static String searchUrl(String text, Set<Language> languages, Set<Category> categories) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/");
        if (text != null && !text.isBlank()) {
            url.queryParam("q", text);
        }
        if (languages != null) {
            languages.forEach(language -> url.queryParam("language", language.code()));
        }
        if (categories != null) {
            categories.forEach(category -> url.queryParam("category", category.name()));
        }
        return url.encode().build().toUriString();
    }
}
