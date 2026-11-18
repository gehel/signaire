package fr.ledcom.signaire.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import fr.ledcom.signaire.extractor.ExtractionException;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
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
    private FakeSource source;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void reset() {
        source.searchedLanguages.clear();
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
                .contains("2 signes trouvés")
                .contains("1 signes écrits dans " + output.resolve("city.zip"));
        assertThat(err.toString()).contains("Échec pour BERNE (2)");
    }

    @Test
    void extractsAllLanguagesByDefault() {
        int exitCode = execute("extraire", "--sortie", output.toString(), "--categorie", "ville", "--sans-medias");

        assertThat(exitCode).isZero();
        assertThat(source.searchedLanguages).contains(Language.FRENCH, Language.GERMAN, Language.ITALIAN);
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

    /** Replaces SignSuisse with two signs in the CITY category, the second one failing. */
    @TestConfiguration
    static class FakeSourceConfiguration {
        @Bean
        FakeSource fakeSource() {
            return new FakeSource();
        }

        @Bean
        @Primary
        SignSourceFactory fakeSignSourceFactory(FakeSource source) {
            return (Duration interval) -> source;
        }
    }

    static final class FakeSource implements SignSource {
        private final List<Language> searchedLanguages = new ArrayList<>();

        @Override
        public List<SearchHit> search(String term, Language language, Category category) {
            searchedLanguages.add(language);
            if (category != Category.CITY || language != Language.FRENCH) {
                return List.of();
            }
            return List.of(new SearchHit("1", "LAUSANNE", language, "https://example.org/1"),
                    new SearchHit("2", "BERNE", language, "https://example.org/2"));
        }

        @Override
        public Sign fetchSign(SearchHit hit) {
            if ("2".equals(hit.uid())) {
                throw new ExtractionException("page indisponible");
            }
            return Sign.builder(hit.uid(), hit.language(), hit.name()).categories(List.of(Category.CITY)).build();
        }

        @Override
        public void download(URI uri, Path target) {
            throw new ExtractionException("no media in this test");
        }
    }
}
