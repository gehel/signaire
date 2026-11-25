package fr.ledcom.signaire.offline;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

class SignLibraryTest {

    private static final Sign LAUSANNE = Sign.builder("1", Language.FRENCH, "LAUSANNE")
            .definition("Capitale du canton de Vaud").categories(List.of(Category.CITY))
            .videos(List.of(new Media("https://example.org/lausanne.mp4", "media/lausanne.mp4"))).build();
    private static final Sign GENEVE = Sign.builder("2", Language.FRENCH, "GENÈVE")
            .definition("Ville au bord du lac Léman").categories(List.of(Category.CITY)).build();
    private static final Sign ZURICH = Sign.builder("3", Language.GERMAN, "ZÜRICH")
            .categories(List.of(Category.CITY)).build();
    private static final Sign LAC = Sign.builder("4", Language.FRENCH, "LAC")
            .periphrase("étendue d'eau").categories(List.of(Category.NATURE, Category.CITY)).build();
    private static final Sign ETOILE = Sign.builder("5", Language.FRENCH, "ÉTOILE")
            .examples(List.of(new Example("Les étoiles brillent sur le lac.", null)))
            .categories(List.of(Category.NATURE)).build();
    private static final Sign LACHEN = Sign.builder("6", Language.GERMAN, "LACHEN")
            .categories(List.of(Category.NATURE)).build();

    @TempDir
    Path tmp;

    private Path directory;
    private SignLibrary library;

    @BeforeEach
    void bundles() throws IOException {
        directory = tmp.resolve("bundles");
        bundle(directory.resolve("city.zip"), Category.CITY, List.of(LAUSANNE, GENEVE, ZURICH, LAC));
        bundle(directory.resolve("nature.zip"), Category.NATURE, List.of(LAC, ETOILE, LACHEN));
        library = new SignLibrary(directory);
        library.load();
    }

    @AfterEach
    void close() {
        library.close();
    }

    @Test
    void knowsTheBundlesAndTheirSigns() {
        assertThat(library.bundles()).extracting(BundleInfo::category, BundleInfo::signCount)
                .containsExactly(tuple(Category.CITY, 4), tuple(Category.NATURE, 3));
        assertThat(library.sign("1")).contains(LAUSANNE);
        assertThat(library.sign("missing")).isEmpty();
    }

    @Test
    void namesComeFirstThenTexts() {
        // the name itself, a name starting with it, then a definition, then an example
        assertThat(names(library.search(Query.forText("lac")))).containsExactly("LAC", "LACHEN", "GENÈVE", "ÉTOILE");
    }

    @Test
    void accentsAndCaseAreIgnored() {
        assertThat(names(library.search(Query.forText("geneve")))).containsExactly("GENÈVE");
        assertThat(names(library.search(Query.forText("Zurich")))).containsExactly("ZÜRICH");
        assertThat(names(library.search(Query.forText("leman")))).containsExactly("GENÈVE");
    }

    @Test
    void everyWordStartsAWordOfTheSign() {
        assertThat(names(library.search(Query.forText("can vau")))).containsExactly("LAUSANNE");
        assertThat(names(library.search(Query.forText("canton berne")))).isEmpty();
    }

    @Test
    void filtersBySignLanguageAndCategory() {
        assertThat(names(library.search(new Query("lac", Set.of(Language.GERMAN), Set.of())))).containsExactly("LACHEN");
        assertThat(names(library.search(new Query(null, Set.of(), Set.of(Category.NATURE)))))
                .containsExactly("ÉTOILE", "LAC", "LACHEN");
    }

    @Test
    void withoutWordsEverySignByNameAccentsIgnored() {
        assertThat(names(library.search(Query.ALL)))
                .containsExactly("ÉTOILE", "GENÈVE", "LAC", "LACHEN", "LAUSANNE", "ZÜRICH");
    }

    @Test
    void signsListedInTheirOrderThoseGoneLeftOut() {
        assertThat(names(library.signs(List.of("3", "gone", "1")))).containsExactly("ZÜRICH", "LAUSANNE");
    }

    @Test
    void bundlesChangedOrRemovedAreNoticed() throws IOException {
        Files.delete(directory.resolve("nature.zip"));
        bundle(directory.resolve("city.zip"), Category.CITY, List.of(LAUSANNE));

        library.load();

        assertThat(library.bundles()).extracting(BundleInfo::category).containsExactly(Category.CITY);
        assertThat(names(library.search(Query.ALL))).containsExactly("LAUSANNE");
    }

    @Test
    void importsBundlesUnderTheNameOfTheirCategory() throws IOException {
        Path elsewhere = bundle(tmp.resolve("downloaded.zip"), Category.FOOD,
                List.of(Sign.builder("7", Language.ITALIAN, "PANE").build()));

        BundleInfo imported;
        try (InputStream in = Files.newInputStream(elsewhere)) {
            imported = library.importBundle(in);
        }

        assertThat(imported.category()).isEqualTo(Category.FOOD);
        assertThat(directory.resolve("food.zip")).exists();
        assertThat(library.sign("7")).isPresent();
    }

    @Test
    void invalidFilesAreNotImported() {
        InputStream notABundle = new ByteArrayInputStream("not a bundle".getBytes(UTF_8));

        assertThatThrownBy(() -> library.importBundle(notABundle)).isInstanceOf(BundleException.class);

        assertThat(library.bundles()).hasSize(2);
    }

    @Test
    void mediaAreTakenFromTheirBundle() throws IOException {
        Path media = library.media(LAUSANNE.videos().getFirst(), tmp.resolve("cache")).orElseThrow();

        assertThat(media).hasContent("video of Lausanne");
        assertThat(library.media(new Media("https://example.org/other.mp4", null), tmp.resolve("cache"))).isEmpty();
    }

    private Path bundle(Path file, Category category, List<Sign> signs) throws IOException {
        Path mediaRoot = Files.createDirectories(tmp.resolve("media-" + category));
        Files.createDirectories(mediaRoot.resolve("media"));
        Files.writeString(mediaRoot.resolve("media/lausanne.mp4"), "video of Lausanne");
        Files.createDirectories(file.getParent());
        return new BundleWriter(Clock.systemUTC()).write(file, category, signs, mediaRoot);
    }

    private static List<String> names(List<Sign> signs) {
        return signs.stream().map(Sign::name).toList();
    }
}
