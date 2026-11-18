package fr.ledcom.signaire.web;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import fr.ledcom.signaire.accounts.SignRequest;
import fr.ledcom.signaire.accounts.SignRequestService;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.extractor.ExtractionException;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/**
 * Administrators handle the requests of users: they look the word up on SignSuisse and import the sign, link
 * a sign of the lexicon to the request, create the sign, or reject the request.
 */
@Controller
public class AdminRequestsController {

    private static final String REDIRECT = "redirect:/admin/requests";

    private final SignRequestService requests;
    private final SignSource signSuisse;
    private final SignSearchService search;
    private final SignEditor editor;
    private final MessageSource messages;

    public AdminRequestsController(SignRequestService requests, SignSource signSuisse, SignSearchService search,
            SignEditor editor, MessageSource messages) {
        this.requests = requests;
        this.signSuisse = signSuisse;
        this.search = search;
        this.editor = editor;
        this.messages = messages;
    }

    @GetMapping("/admin/requests")
    public String open(Model model) {
        model.addAttribute("requests", requests.open());
        return "admin/requests";
    }

    /** The signs SignSuisse has for the word of a request. */
    @GetMapping("/admin/requests/{id}/signsuisse")
    public ModelAndView lookUp(@PathVariable("id") long id, Locale locale) {
        SignRequest request = requests.get(id).orElse(null);
        if (request == null) {
            return new ModelAndView(REDIRECT);
        }
        ModelAndView page = new ModelAndView("admin/request-signsuisse");
        page.addObject("request", request);
        try {
            List<SearchHit> hits = signSuisse.search(request.word(), request.language(), null).stream()
                    .filter(hit -> hit.language() == request.language())
                    .toList();
            Set<String> inLexicon = search.findByUids(hits.stream().map(SearchHit::uid).toList()).stream()
                    .map(Sign::uid).collect(Collectors.toSet());
            page.addObject("hits", hits);
            page.addObject("inLexicon", inLexicon);
        } catch (ExtractionException e) {
            page.addObject("hits", List.of());
            page.addObject("inLexicon", Set.of());
            page.addObject("error", messages.getMessage("admin.requests.importFailed", new Object[] {e.getMessage()},
                    locale));
        }
        return page;
    }

    /** Imports a sign from SignSuisse (its media are linked, not downloaded) and closes the request. */
    @PostMapping("/admin/requests/{id}/import")
    public ModelAndView importSign(@PathVariable("id") long id, @RequestParam("uid") String uid,
            @RequestParam("name") String name, @RequestParam("language") Language language,
            @RequestParam("url") String url, @AuthenticationPrincipal AccountPrincipal admin, Locale locale,
            RedirectAttributes redirect) {
        Sign sign;
        try {
            sign = signSuisse.fetchSign(new SearchHit(uid, name, language, url));
        } catch (ExtractionException | IllegalArgumentException e) {
            redirect.addFlashAttribute("status", messages.getMessage("admin.requests.importFailed",
                    new Object[] {e.getMessage()}, locale));
            return new ModelAndView(REDIRECT);
        }
        editor.save(sign, admin.accountId());
        requests.markAdded(id, sign.uid());
        return new ModelAndView(new RedirectView("/signs/" + sign.uid(), true));
    }

    /** The sign is already in the lexicon: the request is closed with it. */
    @PostMapping("/admin/requests/{id}/link")
    public String link(@PathVariable("id") long id, @RequestParam("uid") String uid) {
        requests.markAdded(id, uid);
        return REDIRECT;
    }

    @PostMapping("/admin/requests/{id}/reject")
    public String reject(@PathVariable("id") long id) {
        requests.reject(id);
        return REDIRECT;
    }
}
