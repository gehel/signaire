package fr.ledcom.signaire.cli;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.bundles.LoadReport;
import fr.ledcom.signaire.core.search.SignIndex;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** {@code signaire charger}: loads bundles into the search index. */
@Component
// a new instance per command line: picocli doesn't reset all options (e.g. positional lists) between runs
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Command(name = "charger", resourceBundle = "fr.ledcom.signaire.cli.load")
public class LoadCommand implements Callable<Integer> {

    private final BundleLoader loader;
    private final SignIndex index;
    private final Messages messages = Messages.of("fr.ledcom.signaire.cli.load");

    @Spec
    private CommandSpec spec;

    @Option(names = {"-b", "--bundles"}, descriptionKey = "bundles", defaultValue = "bundles", paramLabel = "<dir>")
    private Path bundles;

    @Option(names = "--reindexer", descriptionKey = "reindex")
    private boolean reindex;

    @SuppressFBWarnings(value = "FCBL_FIELD_COULD_BE_LOCAL", justification = "Set by picocli through reflection")
    @Option(names = {"-h", "--help"}, usageHelp = true, descriptionKey = "help")
    private boolean helpRequested;

    public LoadCommand(BundleLoader loader, SignIndex index) {
        this.loader = loader;
        this.index = index;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        if (!Files.isDirectory(bundles)) {
            err.println(messages.get("load.noDirectory", bundles));
            return 1;
        }
        if (reindex) {
            index.recreate();
        }
        List<LoadReport> reports = loader.loadDirectory(bundles);
        if (reports.isEmpty()) {
            out.println(messages.get("load.none", bundles));
        }
        boolean failed = false;
        for (LoadReport report : reports) {
            String file = Objects.toString(report.file().getFileName());
            switch (report.status()) {
                case LOADED -> out.println(messages.get("load.loaded", file, report.signCount(),
                        CategoryNames.name(report.category())));
                case UNCHANGED -> out.println(messages.get("load.unchanged", file));
                case FAILED -> {
                    err.println(messages.get("load.failed", file, report.error()));
                    failed = true;
                }
                default -> throw new IllegalStateException("Unknown status " + report.status());
            }
        }
        return failed ? 1 : 0;
    }
}
