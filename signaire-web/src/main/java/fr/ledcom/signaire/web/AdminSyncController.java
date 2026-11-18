package fr.ledcom.signaire.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/** Administrators synchronize the bundles with SignSuisse, and follow the progress. */
@Controller
public class AdminSyncController {

    private final SignSuisseSync sync;
    private final MessageSource messages;

    public AdminSyncController(SignSuisseSync sync, MessageSource messages) {
        this.sync = sync;
        this.messages = messages;
    }

    @GetMapping("/admin/sync")
    public String page(Model model) {
        model.addAttribute("allCategories", Arrays.asList(Category.values()));
        model.addAttribute("allLanguages", Arrays.asList(Language.values()));
        model.addAttribute("progress", sync.progress());
        return "admin/sync";
    }

    /** The progress, refreshed by htmx while the synchronization runs. */
    @GetMapping("/admin/sync/status")
    public String status(Model model) {
        model.addAttribute("progress", sync.progress());
        return "admin/sync :: status";
    }

    @PostMapping("/admin/sync")
    public String start(@RequestParam(name = "categories", required = false) List<Category> categories,
            @RequestParam(name = "languages", required = false) List<Language> languages,
            @RequestParam(name = "withMedia", defaultValue = "false") boolean withMedia, Locale locale,
            RedirectAttributes redirect) {
        if (!sync.start(categories == null ? List.of() : categories, languages == null ? List.of() : languages,
                withMedia)) {
            redirect.addFlashAttribute("status", messages.getMessage("admin.sync.alreadyRunning", null, locale));
        }
        return "redirect:/admin/sync";
    }

    @PostMapping("/admin/sync/cancel")
    public String cancel() {
        sync.cancel();
        return "redirect:/admin/sync";
    }
}
