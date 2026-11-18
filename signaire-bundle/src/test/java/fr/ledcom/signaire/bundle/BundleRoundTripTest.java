package fr.ledcom.signaire.bundle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

class BundleRoundTripTest {

    private static final Instant NOW = Instant.parse("2026-11-17T20:00:00Z");

    @TempDir
    Path tmp;

    private final BundleWriter writer = new BundleWriter(Clock.fixed(NOW, ZoneOffset.UTC));
    private final BundleReader reader = new BundleReader();

    @Test
    void signsSurviveARoundTrip() throws IOException {
        Path mediaRoot = mediaRoot();
        List<Sign> signs = List.of(lausanne(), geneve());

        Path zip = writer.write(tmp.resolve("ville.zip"), Category.CITY, signs, mediaRoot);

        try (Bundle bundle = reader.read(zip)) {
            assertThat(bundle.manifest().formatVersion()).isEqualTo(BundleManifest.CURRENT_FORMAT_VERSION);
            assertThat(bundle.manifest().category()).isEqualTo(Category.CITY);
            assertThat(bundle.manifest().signCount()).isEqualTo(2);
            assertThat(bundle.manifest().createdAt()).isEqualTo(NOW);
            assertThat(bundle.signs()).containsExactlyElementsOf(signs);
        }
    }

    @Test
    void mediaFilesAreStoredInTheBundle() throws IOException {
        Path zip = writer.write(tmp.resolve("ville.zip"), Category.CITY, List.of(lausanne()), mediaRoot());

        try (Bundle bundle = reader.read(zip)) {
            assertThat(bundle.manifest().checksums())
                    .containsOnlyKeys(BundleManifest.SIGNS_ENTRY, "media/lausanne.mp4", "media/lausanne-example.mp4",
                            "media/lausanne.jpg", "media/lausanne-vd.mp4");
            try (InputStream video = bundle.openMedia("media/lausanne.mp4")) {
                assertThat(new String(video.readAllBytes(), UTF_8)).isEqualTo("video of lausanne");
            }
            bundle.verifyMedia();
        }
    }

    @Test
    void mediaThatWasNotDownloadedIsOnlyReferenced() throws IOException {
        Sign sign = Sign.builder("1", Language.FRENCH, "X")
                .categories(List.of(Category.CITY))
                .videos(List.of(new Media("https://example.org/x.mp4", null)))
                .build();

        Path zip = writer.write(tmp.resolve("ville.zip"), Category.CITY, List.of(sign), mediaRoot());

        try (Bundle bundle = reader.read(zip)) {
            assertThat(bundle.signs()).containsExactly(sign);
            assertThat(bundle.manifest().checksums()).containsOnlyKeys(BundleManifest.SIGNS_ENTRY);
        }
    }

    @Test
    void unknownMediaCannotBeOpened() throws IOException {
        Path zip = writer.write(tmp.resolve("ville.zip"), Category.CITY, List.of(lausanne()), mediaRoot());

        try (Bundle bundle = reader.read(zip)) {
            assertThatThrownBy(() -> bundle.openMedia("signs.json")).isInstanceOf(BundleException.class);
            assertThatThrownBy(() -> bundle.openMedia("media/other.mp4")).isInstanceOf(BundleException.class);
        }
    }

    @Test
    void bundlesHaveTheUsualPermissions() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));

        Path zip = writer.write(tmp.resolve("ville.zip"), Category.CITY, List.of(lausanne()), mediaRoot());

        Path usual = Files.writeString(tmp.resolve("usual.txt"), "file created with the umask");
        assertThat(Files.getPosixFilePermissions(zip)).isEqualTo(Files.getPosixFilePermissions(usual));
    }

    @Test
    void writerRejectsMissingMediaFiles() throws IOException {
        Path emptyRoot = Files.createDirectories(tmp.resolve("empty"));

        assertThatThrownBy(() -> writer.write(tmp.resolve("ville.zip"), Category.CITY, List.of(lausanne()), emptyRoot))
                .isInstanceOf(BundleException.class)
                .hasMessageContaining("media/lausanne.mp4");
    }

    @Test
    void writerRejectsMediaPathsOutsideOfTheMediaDirectory() throws IOException {
        Path mediaRoot = mediaRoot();
        Files.writeString(tmp.resolve("secret.txt"), "secret");
        Sign sign = Sign.builder("1", Language.FRENCH, "X")
                .videos(List.of(new Media("https://example.org/x.mp4", "media/../../secret.txt")))
                .build();

        assertThatThrownBy(() -> writer.write(tmp.resolve("ville.zip"), Category.CITY, List.of(sign), mediaRoot))
                .isInstanceOf(BundleException.class)
                .hasMessageContaining("secret.txt");
    }

    private Path mediaRoot() throws IOException {
        Path root = tmp.resolve("download");
        Files.createDirectories(root.resolve("media"));
        Files.writeString(root.resolve("media/lausanne.mp4"), "video of lausanne");
        Files.writeString(root.resolve("media/lausanne-example.mp4"), "example video");
        Files.writeString(root.resolve("media/lausanne.jpg"), "photo");
        Files.writeString(root.resolve("media/lausanne-vd.mp4"), "dialect variant");
        return root;
    }

    private static Sign lausanne() {
        return Sign.builder("119158", Language.FRENCH, "LAUSANNE")
                .sourceUrl("https://www.sgb-fss.ch/signsuisse/fr/lexikon/119158/lausanne")
                .periphrase("Ville, lac, colline.")
                .definition("Lausanne est une ville de Suisse romande.")
                .status("Reconnu")
                .categories(List.of(Category.CITY))
                .dialects(List.of("VD"))
                .videos(List.of(new Media("https://example.org/lausanne.mp4", "media/lausanne.mp4")))
                .examples(List.of(new Example("Lausanne est la capitale du canton de Vaud.",
                        new Media("https://example.org/lausanne-example.mp4", "media/lausanne-example.mp4"))))
                .images(List.of(new Media("https://example.org/lausanne.jpg", "media/lausanne.jpg")))
                .dialectVariants(List.of(new DialectVariant(List.of("VD"),
                        new Media("https://example.org/lausanne-vd.mp4", "media/lausanne-vd.mp4"))))
                .build();
    }

    private static Sign geneve() {
        return Sign.builder("2", Language.FRENCH, "GENÈVE").categories(List.of(Category.CITY)).build();
    }
}
