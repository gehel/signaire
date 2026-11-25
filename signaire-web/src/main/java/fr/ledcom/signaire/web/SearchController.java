package fr.ledcom.signaire.web;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SearchResults;
import fr.ledcom.signaire.core.search.SignQuery;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** The search page, which is also the home page. htmx requests get the results only. */
@Controller
public class SearchController {

    /** Most signs added to a list at once, with "add all the results". */
    static final int MAX_ADDED = 1000;

    private final SignSearchService search;
    private final WordListService lists;
    private final int pageSize;

    public SearchController(SignSearchService search, WordListService lists,
            @Value("${signaire.web.page-size:20}") int pageSize) {
        this.search = search;
        this.lists = lists;
        this.pageSize = pageSize;
    }

    @GetMapping("/")
    public String search(@RequestParam(name = "q", required = false) String text,
            @RequestParam(name = "language", required = false) Set<Language> languages,
            @RequestParam(name = "category", required = false) Set<Category> categories,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @AuthenticationPrincipal AccountPrincipal user,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        Set<Language> selectedLanguages = withoutBlanks(languages);
        Set<Category> selectedCategories = withoutBlanks(categories);
        int currentPage = Math.max(page, 1);
        SignQuery query = query(text, languages, categories, (currentPage - 1) * pageSize, pageSize);
        SearchResults results = search.search(query);
        if (user != null) {
            // to add the results to a list
            model.addAttribute("userLists", lists.lists(user.accountId()));
            model.addAttribute("allCount", Math.min(results.total(), MAX_ADDED));
            // a sign is missing: the user can ask for it
            model.addAttribute("requestWord", results.total() == 0 && text != null && !text.isBlank() ? text.strip()
                    : null);
        }
        model.addAttribute("q", text);
        model.addAttribute("languages", selectedLanguages);
        model.addAttribute("categories", selectedCategories);
        model.addAttribute("allLanguages", Arrays.asList(Language.values()));
        model.addAttribute("allCategories", Arrays.asList(Category.values()));
        model.addAttribute("results", results);
        // listed by name without words: under their initials
        model.addAttribute("initials", Initial.group(results.signs(), query.text() == null));
        model.addAttribute("page", currentPage);
        model.addAttribute("pages", (results.total() + pageSize - 1) / pageSize);
        return Htmx.isPartialRequest(request, response) ? "search-results" : "search";
    }

    /** The search described by the request parameters. */
    static SignQuery query(String text, Collection<Language> languages, Collection<Category> categories, int from,
            int size) {
        return new SignQuery(text, withoutBlanks(languages), withoutBlanks(categories), from, size);
    }

    /** Empty parameters ({@code ?category=}) mean no filter. */
    private static <T> Set<T> withoutBlanks(Collection<T> values) {
        return values == null ? Set.of() : values.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
