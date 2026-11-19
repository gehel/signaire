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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.extractor.CategoryExtractor;
import fr.ledcom.signaire.extractor.ExtractionListener;
import fr.ledcom.signaire.extractor.ExtractionOptions;
import fr.ledcom.signaire.extractor.ExtractionReport;
import fr.ledcom.signaire.extractor.ExtractionStatus;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignEnumerator;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.extractor.SignSuisseClient;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** {@code signaire extraire}: extracts SignSuisse categories into bundles. */
@Component
// a new instance per command line: picocli doesn't reset all options (e.g. positional lists) between runs
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Command(name = "extraire", resourceBundle = "fr.ledcom.signaire.cli.extract")
public class ExtractCommand implements Callable<Integer> {

    /** Prefixes with too many names are searched up to this length. */
    private static final int MAX_QUERY_LENGTH = 12;
    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(10);

    private final SignSourceFactory sourceFactory;
    private final Path mediaCache;
    private final Messages messages = Messages.of("fr.ledcom.signaire.cli.extract");

    @Spec
    private CommandSpec spec;

    // default: signaire.bundles.directory (EnvironmentDefaults)
    @Option(names = {"-o", "--sortie"}, descriptionKey = "output", paramLabel = "<dir>")
    private Path output;

    @Option(names = {"-c", "--categorie"}, descriptionKey = "category", split = ",", paramLabel = "<categorie>",
            converter = CategoryNames.Converter.class, completionCandidates = CategoryNames.Candidates.class)
    private List<Category> categories;

    @Option(names = {"-l", "--langue"}, descriptionKey = "language", split = ",", paramLabel = "<langue>",
            converter = LanguageCodes.Converter.class, completionCandidates = LanguageCodes.Candidates.class)
    private List<Language> languages;

    @Option(names = "--medias", descriptionKey = "medias", paramLabel = "<medias>",
            converter = MediaNames.Converter.class, completionCandidates = MediaNames.Candidates.class)
    private MediaChoice media;

    @Option(names = "--sans-medias", descriptionKey = "noMedia")
    private boolean noMedia;

    @Option(names = "--intervalle", descriptionKey = "interval", defaultValue = "500", paramLabel = "<ms>")
    private long intervalMillis;

    @Option(names = "--rafraichir", descriptionKey = "refresh")
    private boolean refresh;

    @Option(names = {"-h", "--help"}, usageHelp = true, descriptionKey = "help")
    private boolean helpRequested;

    /**
     * @param mediaCache where media are downloaded: the cache directory of the user
     */
    public ExtractCommand(SignSourceFactory sourceFactory, @Value("${signaire.media.cache}") Path mediaCache) {
        this.sourceFactory = sourceFactory;
        this.mediaCache = mediaCache;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        SignSource source = sourceFactory.create(Duration.ofMillis(intervalMillis));
        CategoryExtractor extractor = new CategoryExtractor(source,
                new SignEnumerator(source, SignSuisseClient.MAX_RESULTS, MAX_QUERY_LENGTH),
                new BundleWriter(Clock.systemUTC()), new ProgressListener(), PROGRESS_INTERVAL, Clock.systemUTC());
        EnumSet<Language> selectedLanguages = languages == null || languages.isEmpty()
                ? EnumSet.allOf(Language.class) : EnumSet.copyOf(languages);
        List<Category> selectedCategories = categories == null || categories.isEmpty()
                ? Arrays.asList(Category.values()) : categories;
        for (Category category : selectedCategories) {
            out.println(messages.get("progress.start", CategoryNames.name(category), codes(selectedLanguages)));
            ExtractionReport report = extractor.extract(category, selectedLanguages, output, mediaCache,
                    new ExtractionOptions(mediaChoice(), refresh));
            out.println(messages.get("progress.done", CategoryNames.name(category), report.signCount(),
                    report.bundle().toString(), report.status().requestsDone()));
            if (!report.failures().isEmpty()) {
                spec.commandLine().getErr().println(messages.get("progress.failures", report.failures().size()));
            }
        }
        return 0;
    }

    private MediaChoice mediaChoice() {
        if (noMedia) {
            return MediaChoice.NONE;
        }
        return media == null ? MediaChoice.ALL : media;
    }

    private static String codes(EnumSet<Language> languages) {
        return languages.stream().map(Language::code).collect(Collectors.joining(", "));
    }

    /** Reports progress on the standard and error outputs. */
    private final class ProgressListener implements ExtractionListener {
        @Override
        public void progress(ExtractionStatus status) {
            if (status.stage() == ExtractionStatus.Stage.DONE) {
                return;
            }
            String remaining = status.remaining() == null ? ""
                    : messages.get("progress.remaining", Math.max(1, status.remaining().toMinutes()));
            spec.commandLine().getOut().println(messages.get("progress.status", CategoryNames.name(status.category()),
                    status.searches().sent(), status.searches().pending(), status.signs().found(),
                    status.signs().read(), status.signs().reused(), status.signs().failed(),
                    status.media().downloaded(), status.media().queued(), status.requestsDone(),
                    status.requestsLeft(), remaining));
        }

        @Override
        public void mediaFailed(Sign sign, Media media, RuntimeException cause) {
            spec.commandLine().getErr().println(
                    messages.get("progress.mediaFailed", sign.name(), media.sourceUrl(), cause.getMessage()));
        }

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
