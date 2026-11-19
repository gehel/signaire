package fr.ledcom.signaire.web;

import static fr.ledcom.signaire.web.AdminBundlesController.REDIRECT;
import static fr.ledcom.signaire.web.AdminBundlesController.STATUS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.core.bundles.LoadReport;

/** Administrators upload bundles: checked, stored in the bundle directory under the name of their category, loaded. */
@Controller
public class AdminUploadsController {

    private final BundleUploads uploads;
    private final BundleLoading loading;
    private final MessageSource messages;

    public AdminUploadsController(BundleUploads uploads, BundleLoading loading, MessageSource messages) {
        this.uploads = uploads;
        this.loading = loading;
        this.messages = messages;
    }

    @PostMapping("/admin/bundles")
    public String upload(@RequestParam("bundle") MultipartFile file, Locale locale, RedirectAttributes redirect)
            throws IOException {
        try (InputStream in = file.getInputStream()) {
            Path bundle = uploads.store(in);
            LoadReport report = loading.load(bundle);
            redirect.addFlashAttribute(STATUS, messages.getMessage("admin.bundles.uploaded",
                    new Object[] {String.valueOf(bundle.getFileName()), report.signCount()}, locale));
        } catch (BundleException e) {
            redirect.addFlashAttribute(STATUS, messages.getMessage("admin.bundles.invalid",
                    new Object[] {e.getMessage()}, locale));
        }
        return REDIRECT;
    }
}
