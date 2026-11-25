package fr.ledcom.signaire.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.export.ExportRequest;
import fr.ledcom.signaire.export.PdfLayout;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import jakarta.servlet.http.HttpServletResponse;

/** Downloads of word lists and search results as PDF documents, Anki decks or e-books. */
@Controller
public class ExportController {

    /** Most signs exported from a search. */
    static final int MAX_SEARCH_EXPORT = 500;
    private static final int BATCH_SIZE = 500;

    private final SignSearchService search;
    private final WordListService lists;
    private final WebExports exports;

    public ExportController(SignSearchService search, WordListService lists, WebExports exports) {
        this.search = search;
        this.lists = lists;
        this.exports = exports;
    }

    @GetMapping("/lists/{id}/export")
    public void exportList(@PathVariable("id") long id, @RequestParam("format") WebExports.Format format,
            @RequestParam(name = "paper", defaultValue = "A4") PdfLayout.PaperSize paper,
            @RequestParam(name = "perPage", defaultValue = "4") int perPage,
            @RequestParam(name = "layout", defaultValue = "SHEETS") PdfLayout.Mode layout,
            @RequestParam(name = "videos", defaultValue = "false") boolean videos,
            @AuthenticationPrincipal AccountPrincipal user, Locale locale, HttpServletResponse response)
            throws IOException {
        PdfLayout pdfLayout = new PdfLayout(paper, perPage, layout);
        WordList list = lists.get(user.accountId(), id);
        write(format, new ExportRequest(list.name(), signs(list.signUids()), locale), pdfLayout, videos, response);
    }

    @GetMapping("/export")
    public void exportSearch(@RequestParam("format") WebExports.Format format,
            @RequestParam(name = "q", required = false) @Nullable String text,
            @RequestParam(name = "language", required = false) @Nullable Set<Language> languages,
            @RequestParam(name = "category", required = false) @Nullable Set<Category> categories,
            @RequestParam(name = "paper", defaultValue = "A4") PdfLayout.PaperSize paper,
            @RequestParam(name = "perPage", defaultValue = "4") int perPage,
            @RequestParam(name = "layout", defaultValue = "SHEETS") PdfLayout.Mode layout,
            @RequestParam(name = "videos", defaultValue = "false") boolean videos,
            Locale locale, HttpServletResponse response) throws IOException {
        PdfLayout pdfLayout = new PdfLayout(paper, perPage, layout);
        List<String> uids = search.findUids(SearchController.query(text, languages, categories, 0, 1),
                MAX_SEARCH_EXPORT);
        String title = text == null || text.isBlank() ? "Signaire" : text.strip();
        write(format, new ExportRequest(title, signs(uids), locale), pdfLayout, videos, response);
    }

    /** Impossible layouts, e.g. 5 signs per page. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    private void write(WebExports.Format format, ExportRequest request, PdfLayout layout, boolean videos,
            HttpServletResponse response) throws IOException {
        response.setContentType(format.mediaType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(request.title() + "." + format.extension(), StandardCharsets.UTF_8).build().toString());
        exports.write(format, request, layout, videos, response.getOutputStream());
    }

    private List<Sign> signs(List<String> uids) {
        List<Sign> signs = new ArrayList<>(uids.size());
        for (int start = 0; start < uids.size(); start += BATCH_SIZE) {
            signs.addAll(search.findByUids(uids.subList(start, Math.min(start + BATCH_SIZE, uids.size()))));
        }
        return signs;
    }
}
