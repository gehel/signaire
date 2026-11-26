package fr.ledcom.signaire.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import fr.ledcom.signaire.extractor.FakeSignSource;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.TestSigns;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

@SpringBootTest(classes = {SignaireCliApplication.class, ExtractCommandTest.FakeSourceConfiguration.class})
class ExtractCommandTest {

    @TempDir
    Path output;

    @Autowired
    private IFactory factory;

    @Autowired
    private SignaireCommand command;

    @Autowired
    private FakeSignSource source;

    @Value("${signaire.media.cache}")
    private Path mediaCache;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void reset() throws IOException {
        source.reset().failingPage(TestSigns.GENEVE);
        // media downloaded by a previous test or build would not be downloaded again
        if (Files.isDirectory(mediaCache)) {
            try (Stream<Path> files = Files.walk(mediaCache)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    @Test
    void extractsTheRequestedCategoriesIntoBundles() {
        int exitCode = execute("extraire", "--sortie", output.toString(), "--categorie", "ville", "--langue", "fr",
                "--sans-medias");

        assertThat(exitCode).isZero();
        assertThat(output.resolve("city.zip")).exists();
        assertThat(output.resolve("nation.zip")).doesNotExist();
        assertThat(out.toString())
                .contains("Catégorie ville (fr)")
                .contains("3 signes trouvés")
                .contains("2 signes écrits dans " + output.resolve("city.zip"));
        assertThat(err.toString()).contains("Échec pour GENÈVE (2)");
    }

    @Test
    void extractsAllLanguagesByDefault() {
        int exitCode = execute("extraire", "--sortie", output.toString(), "--categorie", "ville", "--sans-medias");

        assertThat(exitCode).isZero();
        assertThat(source.searches()).extracting(FakeSignSource.Search::language)
                .contains(Language.FRENCH, Language.GERMAN, Language.ITALIAN);
    }

    @Test
    void downloadsTheChosenMedia() {
        int exitCode = execute("extraire", "--sortie", output.toString(), "--categorie", "ville", "--langue", "fr",
                "--medias", "images");

        assertThat(exitCode).isZero();
        assertThat(source.downloads()).containsExactly(URI.create(TestSigns.GARE.images().getFirst().sourceUrl()));
    }

    @Test
    void reusesTheSignsAlreadyExtractedUnlessAskedToReadThemAgain() {
        execute("extraire", "--sortie", output.toString(), "--categorie", "ville", "--langue", "fr", "--sans-medias");
        int read = source.pages().size();

        execute("extraire", "--sortie", output.toString(), "--categorie", "ville", "--langue", "fr", "--sans-medias");
        // GENÈVE failed: it isn't in the bundle
        assertThat(source.pages().subList(read, source.pages().size())).containsExactly(TestSigns.GENEVE.uid());

        read = source.pages().size();
        execute("extraire", "--sortie", output.toString(), "--categorie", "ville", "--langue", "fr", "--sans-medias",
                "--rafraichir");
        assertThat(source.pages().subList(read, source.pages().size())).containsExactlyInAnyOrder(
                TestSigns.GARE.uid(), TestSigns.GENEVE.uid(), TestSigns.LAUSANNE.uid());
    }

    @Test
    void rejectsUnknownMediaChoices() {
        int exitCode = execute("extraire", "--sortie", output.toString(), "--medias", "sons");

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).startsWith("Choix de médias inconnu : sons").contains("tous, videos, images, aucun");
    }

    @Test
    void rejectsUnknownCategories() {
        int exitCode = execute("extraire", "--sortie", output.toString(), "--categorie", "inconnue");

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).startsWith("Catégorie inconnue : inconnue").doesNotContain("Invalid value");
    }

    @Test
    void documentsItsOptionsInFrench() {
        int exitCode = execute("extraire", "--help");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("Extrait des signes de SignSuisse")
                .contains("--sans-medias")
                .contains("ville, circulation");
    }

    private int execute(String... args) {
        CommandLine commandLine = SignaireCommand.commandLine(command, factory);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine.execute(args);
    }

    /** Replaces SignSuisse with the fake one, which tests set up. */
    @TestConfiguration
    static class FakeSourceConfiguration {
        @Bean
        FakeSignSource fakeSource() {
            return new FakeSignSource();
        }

        @Bean
        @Primary
        SignSourceFactory fakeSignSourceFactory(FakeSignSource source) {
            return (Duration interval) -> source;
        }
    }
}
