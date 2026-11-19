package fr.ledcom.signaire.cli;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.core.search.SearchResults;
import fr.ledcom.signaire.core.search.SignQuery;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** {@code signaire chercher}: searches signs. */
@Component
// a new instance per command line: picocli doesn't reset all options (e.g. positional lists) between runs
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Command(name = "chercher", resourceBundle = "fr.ledcom.signaire.cli.search")
public class SearchCommand implements Runnable {

    private final SignSearchService search;
    private final Messages messages = Messages.of("fr.ledcom.signaire.cli.search");

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

    @Option(names = {"-n", "--nombre"}, descriptionKey = "size", defaultValue = "20", paramLabel = "<n>")
    private int size;

    @Option(names = {"-p", "--page"}, descriptionKey = "page", defaultValue = "1", paramLabel = "<n>")
    private int page;

    @SuppressFBWarnings(value = "FCBL_FIELD_COULD_BE_LOCAL", justification = "Set by picocli through reflection")
    @Option(names = {"-h", "--help"}, usageHelp = true, descriptionKey = "help")
    private boolean helpRequested;

    public SearchCommand(SignSearchService search) {
        this.search = search;
    }

    @Override
    public void run() {
        PrintWriter out = spec.commandLine().getOut();
        String text = words == null ? null : String.join(" ", words);
        SignQuery query = new SignQuery(text, languages == null ? null : Set.copyOf(languages),
                categories == null ? null : Set.copyOf(categories), (Math.max(page, 1) - 1) * size, size);
        SearchResults results = search.search(query);
        if (results.total() == 0) {
            out.println(messages.get("search.none"));
            return;
        }
        out.println(messages.get("search.found", results.total()));
        for (Sign sign : results.signs()) {
            out.println();
            print(out, sign);
        }
        long pages = (results.total() + size - 1) / size;
        if (pages > 1) {
            out.println();
            out.println(messages.get("search.page", Math.max(page, 1), pages));
        }
    }

    private void print(PrintWriter out, Sign sign) {
        if (sign.categories().isEmpty()) {
            out.println(messages.get("search.sign", sign.name(), sign.language().code()));
        } else {
            out.println(messages.get("search.signWithCategories", sign.name(), sign.language().code(),
                    sign.categories().stream().map(CategoryNames::name).collect(Collectors.joining(", "))));
        }
        String description = sign.definition() != null ? sign.definition() : sign.periphrase();
        if (description != null) {
            out.println("  " + description);
        }
        if (sign.sourceUrl() != null) {
            out.println("  " + sign.sourceUrl());
        }
    }
}
