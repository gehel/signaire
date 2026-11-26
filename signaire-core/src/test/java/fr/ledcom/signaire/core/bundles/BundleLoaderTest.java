package fr.ledcom.signaire.core.bundles;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.model.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.core.search.SignQuery;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Sign;

class BundleLoaderTest {

    @TempDir
    Path bundles;

    @TempDir
    Path directory;

    private SignIndex index;
    private BundleLoader loader;
    private SignSearchService search;

    private final BundleWriter writer = new BundleWriter(Clock.systemUTC());

    @BeforeEach
    void openIndex() {
        index = new SignIndex(directory);
        loader = new BundleLoader(index);
        search = new SignSearchService(index);
    }

    @AfterEach
    void closeIndex() throws IOException {
        index.close();
    }

    @Test
    void loadsAllBundlesOfADirectory() throws IOException {
        write("city.zip", Category.CITY, List.of(TestSigns.LAUSANNE, TestSigns.GENEVE));
        write("animals.zip", Category.ANIMALS, List.of(TestSigns.CHAT, TestSigns.KATZE));
        Files.writeString(bundles.resolve("notes.txt"), "not a bundle");

        List<LoadReport> reports = loader.loadDirectory(bundles);

        assertThat(reports).extracting(LoadReport::category).containsExactlyInAnyOrder(Category.CITY, Category.ANIMALS);
        assertThat(reports).allSatisfy(report -> assertThat(report.status()).isEqualTo(LoadReport.Status.LOADED));
        assertThat(search.search(SignQuery.all(0, 10)).total()).isEqualTo(4);
    }

    @Test
    void findsTheOriginalOfASignInTheBundles() throws IOException {
        write("city.zip", Category.CITY, List.of(TestSigns.LAUSANNE, TestSigns.GENEVE));
        write("animals.zip", Category.ANIMALS, List.of(TestSigns.CHAT));

        assertThat(loader.findInBundles(bundles, TestSigns.CHAT.uid())).contains(TestSigns.CHAT);
        assertThat(loader.findInBundles(bundles, "unknown")).isEmpty();
    }

    @Test
    void listsTheLoadedBundles() throws IOException {
        write("city.zip", Category.CITY, List.of(TestSigns.LAUSANNE, TestSigns.GENEVE));
        loader.loadDirectory(bundles);

        assertThat(loader.loadedBundles()).singleElement().satisfies(bundle -> {
            assertThat(bundle.file()).isEqualTo("city.zip");
            assertThat(bundle.category()).isEqualTo(Category.CITY);
            assertThat(bundle.signCount()).isEqualTo(2);
        });
    }

    @Test
    void deletesSignsFromTheIndex() throws IOException {
        write("city.zip", Category.CITY, List.of(TestSigns.LAUSANNE, TestSigns.GENEVE));
        loader.loadDirectory(bundles);

        index.delete(TestSigns.LAUSANNE.uid());
        index.refresh();

        assertThat(search.findByUid(TestSigns.LAUSANNE.uid())).isEmpty();
        assertThat(search.findByUid(TestSigns.GENEVE.uid())).isPresent();
    }

    @Test
    void skipsBundlesThatAreAlreadyLoaded() throws IOException {
        Path bundle = write("city.zip", Category.CITY, List.of(TestSigns.LAUSANNE));
        loader.load(bundle);

        LoadReport second = loader.load(bundle);

        assertThat(second.status()).isEqualTo(LoadReport.Status.UNCHANGED);
    }

    @Test
    void reloadsBundlesThatChanged() throws IOException {
        Path bundle = write("city.zip", Category.CITY, List.of(TestSigns.LAUSANNE));
        loader.load(bundle);
        write("city.zip", Category.CITY, List.of(TestSigns.LAUSANNE, TestSigns.GENEVE));

        LoadReport second = loader.load(bundle);

        assertThat(second.status()).isEqualTo(LoadReport.Status.LOADED);
        assertThat(second.signCount()).isEqualTo(2);
        assertThat(search.search(SignQuery.forText("geneve")).signs()).extracting(Sign::uid)
                .containsExactly(TestSigns.GENEVE.uid());
    }

    @Test
    void emptyingTheIndexForgetsTheLoadedBundles() throws IOException {
        Path bundle = write("city.zip", Category.CITY, List.of(TestSigns.LAUSANNE));
        loader.load(bundle);

        index.recreate();

        assertThat(loader.loadedBundles()).isEmpty();
        assertThat(loader.load(bundle).status()).isEqualTo(LoadReport.Status.LOADED);
    }

    @Test
    void reportsInvalidBundlesWithoutStopping() throws IOException {
        Files.writeString(bundles.resolve("broken.zip"), "not a zip");
        write("city.zip", Category.CITY, List.of(TestSigns.LAUSANNE));

        List<LoadReport> reports = loader.loadDirectory(bundles);

        assertThat(reports).extracting(LoadReport::status)
                .containsExactlyInAnyOrder(LoadReport.Status.FAILED, LoadReport.Status.LOADED);
        assertThat(reports).filteredOn(report -> report.status() == LoadReport.Status.FAILED)
                .singleElement().extracting(LoadReport::error).asString().contains("broken.zip");
    }

    private Path write(String name, Category category, List<Sign> signs) throws IOException {
        Path media = Files.createDirectories(bundles.resolve(".media"));
        Files.createDirectories(media.resolve("media"));
        Files.writeString(media.resolve("media/lausanne.mp4"), "video");
        return writer.write(bundles.resolve(name), category, signs, media);
    }
}
