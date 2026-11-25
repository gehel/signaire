package fr.ledcom.signaire.web;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListException;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Sign;

/** The word lists of the logged in user. Lists of other users are reported as not found. */
@Controller
public class ListController {

    private static final String LISTS_VIEW = "lists";
    private static final String LIST_VIEW = "list";

    private final WordListService lists;
    private final SignSearchService search;

    public ListController(WordListService lists, SignSearchService search) {
        this.lists = lists;
        this.search = search;
    }

    @GetMapping("/lists")
    public String lists(@AuthenticationPrincipal AccountPrincipal user, Model model) {
        addListsModel(user, model, null, "");
        return LISTS_VIEW;
    }

    @PostMapping("/lists")
    public String create(@RequestParam("name") String name, @AuthenticationPrincipal AccountPrincipal user,
            Model model) {
        try {
            return "redirect:/lists/" + lists.create(user.accountId(), name).id();
        } catch (WordListException e) {
            addListsModel(user, model, errorKey(e), name);
            return LISTS_VIEW;
        }
    }

    @GetMapping("/lists/{id}")
    public String list(@PathVariable("id") long id, @AuthenticationPrincipal AccountPrincipal user, Model model) {
        addListModel(lists.get(user.accountId(), id), model, null);
        return LIST_VIEW;
    }

    @PostMapping("/lists/{id}/rename")
    public String rename(@PathVariable("id") long id, @RequestParam("name") String name,
            @AuthenticationPrincipal AccountPrincipal user, Model model) {
        try {
            lists.rename(user.accountId(), id, name);
            return "redirect:/lists/" + id;
        } catch (WordListException e) {
            addListModel(lists.get(user.accountId(), id), model, errorKey(e));
            return LIST_VIEW;
        }
    }

    @PostMapping("/lists/{id}/delete")
    public String delete(@PathVariable("id") long id, @AuthenticationPrincipal AccountPrincipal user) {
        lists.delete(user.accountId(), id);
        return "redirect:/lists";
    }

    @PostMapping("/lists/{id}/signs/{uid}/remove")
    public String remove(@PathVariable("id") long id, @PathVariable("uid") String uid,
            @AuthenticationPrincipal AccountPrincipal user) {
        lists.remove(user.accountId(), id, uid);
        return "redirect:/lists/" + id;
    }

    private void addListsModel(AccountPrincipal user, Model model, @Nullable String error, String name) {
        model.addAttribute("lists", lists.lists(user.accountId()));
        model.addAttribute("error", error);
        model.addAttribute("name", name);
        model.addAttribute("maxNameLength", WordListService.MAX_NAME_LENGTH);
    }

    private void addListModel(WordList list, Model model, @Nullable String error) {
        List<Sign> signs = search.findByUids(list.signUids());
        model.addAttribute("list", list);
        model.addAttribute("signs", signs);
        model.addAttribute("missing", list.size() - signs.size());
        model.addAttribute("error", error);
        model.addAttribute("maxNameLength", WordListService.MAX_NAME_LENGTH);
    }

    /** Invalid or taken names; lists not found are left to {@link ListErrors}. */
    private static String errorKey(WordListException e) {
        if (e.reason() == WordListException.Reason.NOT_FOUND) {
            throw e;
        }
        return "lists.error." + e.reason().name();
    }
}
