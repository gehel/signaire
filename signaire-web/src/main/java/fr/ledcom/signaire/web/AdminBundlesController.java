package fr.ledcom.signaire.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.bundles.BundleRecord;
import fr.ledcom.signaire.core.bundles.LoadReport;

/** Administrators see the bundles, reload and reindex them (uploads: {@link AdminUploadsController}). */
@Controller
public class AdminBundlesController {

    static final String STATUS = "status";
    static final String REDIRECT = "redirect:/admin/bundles";

    private final BundleLoader loader;
    private final BundleLoading loading;
    private final MessageSource messages;
    private final Path directory;

    public AdminBundlesController(BundleLoader loader, BundleLoading loading, MessageSource messages,
            @Value("${signaire.bundles.directory:bundles}") Path directory) {
        this.loader = loader;
        this.loading = loading;
        this.messages = messages;
        this.directory = directory;
    }

    /**
     * A bundle file of the directory.
     *
     * @param loaded what the index knows of the bundle, null when it was not loaded
     */
    public record BundleFile(String name, long size, @Nullable BundleRecord loaded) {
    }

    @GetMapping("/admin/bundles")
    public String bundles(Model model) throws IOException {
        Map<String, BundleRecord> loaded = loader.loadedBundles().stream()
                .collect(Collectors.toMap(BundleRecord::file, Function.identity()));
        model.addAttribute("files", files(loaded));
        model.addAttribute("directory", directory.toAbsolutePath());
        return "admin/bundles";
    }

    @PostMapping("/admin/bundles/reload")
    public String reload(Locale locale, RedirectAttributes redirect) {
        redirect.addFlashAttribute(STATUS, summary(loading.load(), locale));
        return REDIRECT;
    }

    /** Empties the index and loads all the bundles again: after a change of the index mapping. */
    @PostMapping("/admin/bundles/reindex")
    public String reindex(Locale locale, RedirectAttributes redirect) {
        redirect.addFlashAttribute(STATUS, summary(loading.reindex(), locale));
        return REDIRECT;
    }

    private List<BundleFile> files(Map<String, BundleRecord> loaded) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> zips = files.filter(file -> String.valueOf(file.getFileName()).endsWith(".zip")).sorted()
                    .toList();
            List<BundleFile> result = new ArrayList<>();
            for (Path zip : zips) {
                String name = String.valueOf(zip.getFileName());
                result.add(new BundleFile(name, Files.size(zip), loaded.get(name)));
            }
            return result;
        }
    }

    private String summary(List<LoadReport> reports, Locale locale) {
        Map<LoadReport.Status, Long> counts = reports.stream()
                .collect(Collectors.groupingBy(LoadReport::status, Collectors.counting()));
        return messages.getMessage("admin.bundles.loaded", new Object[] {
            counts.getOrDefault(LoadReport.Status.LOADED, 0L),
            counts.getOrDefault(LoadReport.Status.UNCHANGED, 0L),
            counts.getOrDefault(LoadReport.Status.FAILED, 0L)}, locale);
    }
}
