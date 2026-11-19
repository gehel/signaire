package fr.ledcom.signaire.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.core.search.SignQuery;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.export.ExportRequest;
import fr.ledcom.signaire.export.PdfLayout;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** {@code signaire exporter}: exports the results of a search as a PDF, an Anki deck or an e-book. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Command(name = "exporter", resourceBundle = "fr.ledcom.signaire.cli.export")
public class ExportCommand implements Callable<Integer> {

    /** Signs are fetched from the index in batches. */
    private static final int BATCH_SIZE = 500;

    private final SignSearchService search;
    private final Messages messages = Messages.of("fr.ledcom.signaire.cli.export");

    @Spec
    private CommandSpec spec;

    @Parameters(arity = "0..*", descriptionKey = "words", paramLabel = "<mot>")
    private List<String> words;

    @Option(names = {"-l", "--langue"}, descriptionKey = "language", split = ",", paramLabel = "<langue>",
            converter = LanguageCodes.Converter.class, completionCandidates = LanguageCodes.Candidates.class)
    private List<Language> languages;

    @Option(names = {"-c", "--categorie"}, descriptionKey = "category", split = ",", paramLabel = "<categorie>",
            converter = CategoryNames.Converter.class, completionCandidates = CategoryNames.Candidates.class)
    private List<Category> categories;

    @Option(names = {"-f", "--format"}, descriptionKey = "format", required = true, paramLabel = "<format>")
    private Exports.Format format;

    @Option(names = {"-o", "--sortie"}, descriptionKey = "output", required = true, paramLabel = "<fichier>")
    private Path output;

    @Option(names = {"-t", "--titre"}, descriptionKey = "title", paramLabel = "<titre>")
    private String title;

    @Option(names = "--papier", descriptionKey = "paper", defaultValue = "A4", paramLabel = "<papier>")
    private PdfLayout.PaperSize paper;

    @Option(names = "--par-page", descriptionKey = "perPage", defaultValue = "4", paramLabel = "<n>")
    private int perPage;

    @Option(names = "--mise-en-page", descriptionKey = "layout", paramLabel = "<mise-en-page>",
            converter = LayoutNames.Converter.class, completionCandidates = LayoutNames.Candidates.class)
    private PdfLayout.Mode layout;

    @Option(names = "--avec-videos", descriptionKey = "withVideos")
    private boolean withVideos;

    // default: signaire.bundles.directory (EnvironmentDefaults)
    @Option(names = {"-b", "--bundles"}, descriptionKey = "bundles",
            paramLabel = "<repertoire>")
    private Path bundles;

    @Option(names = {"-n", "--max"}, descriptionKey = "max", defaultValue = "1000", paramLabel = "<n>")
    private int max;

    @SuppressFBWarnings(value = "FCBL_FIELD_COULD_BE_LOCAL", justification = "Set by picocli through reflection")
    @Option(names = {"-h", "--help"}, usageHelp = true, descriptionKey = "help")
    private boolean helpRequested;

    public ExportCommand(SignSearchService search) {
        this.search = search;
    }

    @Override
    public Integer call() throws IOException {
        PdfLayout pdfLayout = pdfLayout();
        String text = words == null ? null : String.join(" ", words);
        List<Sign> signs = signs(new SignQuery(text, languages == null ? null : Set.copyOf(languages),
                categories == null ? null : Set.copyOf(categories), 0, 1));
        ExportRequest request = new ExportRequest(documentTitle(text), signs, Locale.getDefault());
        Exports.write(format, request, pdfLayout, withVideos, bundles, output);
        PrintWriter console = spec.commandLine().getOut();
        console.println(messages.get("export.done", signs.size(), output));
        return 0;
    }

    /** The title given, else the words searched, else a default title. */
    private String documentTitle(String text) {
        if (title != null) {
            return title;
        }
        return text != null ? text : messages.get("export.defaultTitle");
    }

    private PdfLayout pdfLayout() {
        try {
            return new PdfLayout(paper, perPage, layout == null ? PdfLayout.Mode.SHEETS : layout);
        } catch (IllegalArgumentException e) {
            throw new ParameterException(spec.commandLine(), messages.get("error.perPage", perPage), e, null,
                    String.valueOf(perPage));
        }
    }

    private List<Sign> signs(SignQuery query) {
        List<String> uids = search.findUids(query, max);
        List<Sign> signs = new ArrayList<>(uids.size());
        for (int start = 0; start < uids.size(); start += BATCH_SIZE) {
            signs.addAll(search.findByUids(uids.subList(start, Math.min(start + BATCH_SIZE, uids.size()))));
        }
        return signs;
    }
}
