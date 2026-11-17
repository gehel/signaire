package fr.ledcom.signaire.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.extractor.CategoryExtractor;
import fr.ledcom.signaire.extractor.ExtractionListener;
import fr.ledcom.signaire.extractor.ExtractionReport;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignEnumerator;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.extractor.SignSuisseClient;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** {@code signaire extraire}: extracts SignSuisse categories into bundles. */
@Component
@Command(name = "extraire", resourceBundle = "fr.ledcom.signaire.cli.extract")
public class ExtractCommand implements Callable<Integer> {

    /** Truncated searches are refined up to this query length. */
    private static final int MAX_QUERY_LENGTH = 6;

    private final SignSourceFactory sourceFactory;
    private final Messages messages = Messages.of("fr.ledcom.signaire.cli.extract");

    @Spec
    private CommandSpec spec;

    @Option(names = {"-o", "--sortie"}, descriptionKey = "output", defaultValue = "bundles", paramLabel = "<dir>")
    private Path output;

    @Option(names = {"-c", "--categorie"}, descriptionKey = "category", split = ",", paramLabel = "<categorie>",
            converter = CategoryNames.Converter.class, completionCandidates = CategoryNames.Candidates.class)
    private List<Category> categories;

    @Option(names = {"-l", "--langue"}, descriptionKey = "language", split = ",", paramLabel = "<langue>",
            converter = LanguageCodes.Converter.class, completionCandidates = LanguageCodes.Candidates.class)
    private List<Language> languages;

    @Option(names = "--sans-medias", descriptionKey = "noMedia")
    private boolean noMedia;

    @Option(names = "--intervalle", descriptionKey = "interval", defaultValue = "500", paramLabel = "<ms>")
    private long intervalMillis;

    @Option(names = {"-h", "--help"}, usageHelp = true, descriptionKey = "help")
    private boolean helpRequested;

    public ExtractCommand(SignSourceFactory sourceFactory) {
        this.sourceFactory = sourceFactory;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        SignSource source = sourceFactory.create(Duration.ofMillis(intervalMillis));
        CategoryExtractor extractor = new CategoryExtractor(source,
                new SignEnumerator(source, SignSuisseClient.MAX_RESULTS, MAX_QUERY_LENGTH),
                new BundleWriter(Clock.systemUTC()), new ProgressListener());
        EnumSet<Language> selectedLanguages = languages == null || languages.isEmpty()
                ? EnumSet.allOf(Language.class) : EnumSet.copyOf(languages);
        List<Category> selectedCategories = categories == null || categories.isEmpty()
                ? Arrays.asList(Category.values()) : categories;
        for (Category category : selectedCategories) {
            out.println(messages.get("progress.start", CategoryNames.name(category), codes(selectedLanguages)));
            ExtractionReport report = extractor.extract(category, selectedLanguages, output, !noMedia);
            out.println(messages.get("progress.done", CategoryNames.name(category), report.signCount(),
                    report.bundle().toString()));
            if (!report.failures().isEmpty()) {
                spec.commandLine().getErr().println(messages.get("progress.failures", report.failures().size()));
            }
        }
        return 0;
    }

    private static String codes(EnumSet<Language> languages) {
        return languages.stream().map(Language::code).collect(Collectors.joining(", "));
    }

    /** Reports progress on the standard and error outputs. */
    private final class ProgressListener implements ExtractionListener {
        @Override
        public void enumerated(Category category, Language language, SignEnumerator.Result result) {
            spec.commandLine().getOut().println(messages.get("progress.enumerated", CategoryNames.name(category),
                    language.code(), result.hits().size(), result.queries()));
            if (!result.complete()) {
                spec.commandLine().getErr().println(
                        messages.get("progress.incomplete", CategoryNames.name(category), language.code()));
            }
        }

        @Override
        public void signFailed(SearchHit hit, RuntimeException cause) {
            spec.commandLine().getErr().println(
                    messages.get("progress.failed", hit.name(), hit.uid(), cause.getMessage()));
        }
    }
}
