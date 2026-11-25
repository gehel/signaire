package fr.ledcom.signaire.core.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.model.TestSigns;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

class MediaLibraryTest {

    @TempDir
    Path bundles;

    @TempDir
    Path mediaRoot;

    private final BundleWriter writer = new BundleWriter(Clock.systemUTC());
    private MediaLibrary library;

    @BeforeEach
    void createLibrary() {
        library = new MediaLibrary(bundles);
    }

    @AfterEach
    void closeLibrary() {
        library.close();
    }

    @Test
    void findsTheMediaOfTheBundlesOfItsDirectory() throws IOException {
        writeCityBundle("video");

        Optional<Resource> media = library.find("media/lausanne.mp4");

        assertThat(media).isPresent();
        assertThat(media.get().getContentAsByteArray()).asString().isEqualTo("video");
        assertThat(media.get().contentLength()).isEqualTo(5);
        assertThat(media.get().getFilename()).isEqualTo("lausanne.mp4");
    }

    @Test
    void doesNotServeTheBundleMetadata() throws IOException {
        writeCityBundle("video");

        assertThat(library.find("signs.json")).isEmpty();
        assertThat(library.find("manifest.json")).isEmpty();
    }

    @Test
    void unknownMediaAreNotFound() throws IOException {
        writeCityBundle("video");

        assertThat(library.find("media/unknown.mp4")).isEmpty();
        assertThat(library.find("../city.zip")).isEmpty();
    }

    @Test
    void findsTheMediaOfBundlesAddedLater() throws IOException {
        assertThat(library.find("media/lausanne.mp4")).isEmpty();

        writeCityBundle("video");

        assertThat(library.find("media/lausanne.mp4")).isPresent();
    }

    @Test
    void servesTheMediaOfReplacedBundles() throws IOException {
        writeCityBundle("video");
        assertThat(library.find("media/lausanne.mp4")).isPresent();

        writeCityBundle("new video");
        library.refresh();

        assertThat(library.find("media/lausanne.mp4").orElseThrow().getContentAsByteArray()).asString()
                .isEqualTo("new video");
    }

    @Test
    void forgetsTheMediaOfRemovedBundles() throws IOException {
        writeCityBundle("video");
        assertThat(library.find("media/lausanne.mp4")).isPresent();

        Files.delete(bundles.resolve("city.zip"));
        library.refresh();

        assertThat(library.find("media/lausanne.mp4")).isEmpty();
    }

    @Test
    void aMissingDirectoryHasNoMedia() {
        try (MediaLibrary missing = new MediaLibrary(bundles.resolve("missing"))) {
            assertThat(missing.find("media/lausanne.mp4")).isEmpty();
        }
    }

    @Test
    void ignoresInvalidBundles() throws IOException {
        Files.writeString(bundles.resolve("broken.zip"), "not a zip");
        writeCityBundle("video");

        assertThat(library.find("media/lausanne.mp4")).isPresent();
    }

    private void writeCityBundle(String video) throws IOException {
        Path media = Files.createDirectories(mediaRoot.resolve("media"));
        Files.writeString(media.resolve("lausanne.mp4"), video);
        Sign lausanne = TestSigns.LAUSANNE.toBuilder()
                .videos(List.of(new Media("https://example.org/lausanne.mp4", "media/lausanne.mp4")))
                .build();
        writer.write(bundles.resolve("city.zip"), Category.CITY, List.of(lausanne), mediaRoot);
    }
}
