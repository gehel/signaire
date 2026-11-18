package fr.ledcom.signaire.core.bundles;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.core.CoreTestApplication;
import fr.ledcom.signaire.core.ElasticsearchTestConfiguration;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.core.search.SignQuery;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Sign;

@SpringBootTest(classes = CoreTestApplication.class)
@Import(ElasticsearchTestConfiguration.class)
class BundleLoaderIT {

    @TempDir
    Path bundles;

    @Autowired
    private SignIndex index;

    @Autowired
    private BundleLoader loader;

    @Autowired
    private SignSearchService search;

    private final BundleWriter writer = new BundleWriter(Clock.systemUTC());

    @BeforeEach
    void emptyIndex() {
        index.recreate();
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
