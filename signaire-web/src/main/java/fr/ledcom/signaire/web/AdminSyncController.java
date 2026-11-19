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

import fr.ledcom.signaire.extractor.ExtractionOptions;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.extractor.UserAgent;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/** Administrators synchronize the bundles with SignSuisse, and follow the progress. */
@Controller
public class AdminSyncController {

    private final SignSuisseSync sync;
    private final MessageSource messages;
    private final UserAgent userAgent;

    public AdminSyncController(SignSuisseSync sync, MessageSource messages, UserAgent userAgent) {
        this.sync = sync;
        this.messages = messages;
        this.userAgent = userAgent;
    }

    @GetMapping("/admin/sync")
    public String page(Model model) {
        model.addAttribute("allCategories", Arrays.asList(Category.values()));
        model.addAttribute("allLanguages", Arrays.asList(Language.values()));
        model.addAttribute("allMedia", Arrays.asList(MediaChoice.values()));
        model.addAttribute("progress", sync.progress());
        model.addAttribute("userAgent", userAgent);
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
            @RequestParam(name = "media", defaultValue = "NONE") MediaChoice media,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh, Locale locale,
            RedirectAttributes redirect) {
        if (!sync.start(categories == null ? List.of() : categories, languages == null ? List.of() : languages,
                new ExtractionOptions(media, refresh))) {
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
