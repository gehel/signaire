package fr.ledcom.signaire.web;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import fr.ledcom.signaire.accounts.SignOverrideService;
import fr.ledcom.signaire.accounts.SignRequestService;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/** Administrators add, edit, delete signs, and undo their changes. */
@Controller
public class AdminSignsController {

    private static final String FORM_VIEW = "admin/sign-form";

    private final SignEditor editor;
    private final SignOverrideService overrides;
    private final SignSearchService search;
    private final SignRequestService requests;

    public AdminSignsController(SignEditor editor, SignOverrideService overrides, SignSearchService search,
            SignRequestService requests) {
        this.editor = editor;
        this.overrides = overrides;
        this.search = search;
        this.requests = requests;
    }

    @ModelAttribute("allLanguages")
    public List<Language> allLanguages() {
        return Arrays.asList(Language.values());
    }

    @ModelAttribute("allCategories")
    public List<Category> allCategories() {
        return Arrays.asList(Category.values());
    }

    /** The changes made to the signs of the bundles. */
    @GetMapping("/admin/signs")
    public String changes(Model model) {
        model.addAttribute("overrides", overrides.all());
        return "admin/signs";
    }

    /**
     * @param requestId the request the sign is for, whose word and language fill the form
     */
    @GetMapping("/admin/signs/new")
    public String newSign(@RequestParam(name = "request", required = false) Long requestId, Model model) {
        SignForm form = Optional.ofNullable(requestId).flatMap(requests::get)
                .map(request -> SignForm.empty(request.word(), request.language().code()))
                .orElseGet(() -> SignForm.empty("", Language.FRENCH.code()));
        model.addAttribute("form", form);
        model.addAttribute("uid", null);
        model.addAttribute("requestId", requestId);
        return FORM_VIEW;
    }

    @PostMapping("/admin/signs")
    public ModelAndView create(@ModelAttribute("form") SignForm form,
            @RequestParam(name = "request", required = false) Long requestId,
            @AuthenticationPrincipal AccountPrincipal admin, Model model) {
        List<String> errors = form.errors();
        if (!errors.isEmpty()) {
            model.addAttribute("requestId", requestId);
            return formWithErrors(model, null, errors);
        }
        Sign sign = form.toSign(overrides.newUid(), null);
        editor.save(sign, admin.accountId());
        if (requestId != null) {
            requests.markAdded(requestId, sign.uid());
        }
        return new ModelAndView(new RedirectView("/signs/" + sign.uid(), true));
    }

    @GetMapping("/admin/signs/{uid}/edit")
    public ModelAndView edit(@PathVariable("uid") String uid, Model model) {
        return search.findByUid(uid)
                .map(sign -> {
                    model.addAttribute("form", SignForm.of(sign));
                    model.addAttribute("uid", uid);
                    return new ModelAndView(FORM_VIEW, model.asMap());
                })
                .orElseGet(AdminSignsController::notFound);
    }

    @PostMapping("/admin/signs/{uid}")
    public ModelAndView save(@PathVariable("uid") String uid, @ModelAttribute("form") SignForm form,
            @AuthenticationPrincipal AccountPrincipal admin, Model model) {
        List<String> errors = form.errors();
        if (!errors.isEmpty()) {
            return formWithErrors(model, uid, errors);
        }
        editor.save(form.toSign(uid, search.findByUid(uid).orElse(null)), admin.accountId());
        return new ModelAndView(new RedirectView("/signs/{uid}", true));
    }

    @PostMapping("/admin/signs/{uid}/delete")
    public String delete(@PathVariable("uid") String uid, @AuthenticationPrincipal AccountPrincipal admin) {
        editor.delete(uid, admin.accountId());
        return "redirect:/admin/signs";
    }

    @PostMapping("/admin/signs/{uid}/revert")
    public String revert(@PathVariable("uid") String uid) {
        editor.revert(uid);
        return "redirect:/admin/signs";
    }

    private static ModelAndView formWithErrors(Model model, String uid, List<String> errors) {
        model.addAttribute("uid", uid);
        model.addAttribute("errors", errors);
        return new ModelAndView(FORM_VIEW, model.asMap());
    }

    private static ModelAndView notFound() {
        return new ModelAndView("error/sign-not-found", HttpStatus.NOT_FOUND);
    }
}
