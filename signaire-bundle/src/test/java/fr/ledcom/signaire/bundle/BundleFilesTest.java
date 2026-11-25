package fr.ledcom.signaire.bundle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

class BundleFilesTest {

    @TempDir
    Path tmp;

    @Test
    void bundlesAreNamedAfterTheirCategory() {
        assertThat(BundleFiles.fileName(Category.CITY)).isEqualTo("city.zip");
        assertThat(BundleFiles.fileName(Category.SPORT_LEISURE)).isEqualTo("sport-leisure.zip");
    }

    @Test
    void storesACheckedBundleUnderTheNameOfItsCategory() throws IOException {
        Path made = bundle(tmp.resolve("made.zip"));
        Path directory = tmp.resolve("bundles");

        Path stored;
        try (InputStream in = Files.newInputStream(made)) {
            stored = BundleFiles.store(in, directory);
        }

        assertThat(stored).isEqualTo(directory.resolve("city.zip"));
        try (Bundle bundle = new BundleReader().read(stored)) {
            assertThat(bundle.signs()).extracting(Sign::name).containsExactly("LAUSANNE");
        }
        assertThat(directory).isDirectoryContaining("glob:**/city.zip").isDirectoryNotContaining("glob:**.tmp");
    }

    @Test
    void anInvalidFileNeverReplacesABundle() throws IOException {
        Path directory = tmp.resolve("bundles");
        Path existing = bundle(Files.createDirectories(directory).resolve("city.zip"));
        byte[] before = Files.readAllBytes(existing);

        InputStream notAZip = new ByteArrayInputStream("not a zip".getBytes(UTF_8));

        assertThatThrownBy(() -> BundleFiles.store(notAZip, directory)).isInstanceOf(BundleException.class);

        assertThat(Files.readAllBytes(existing)).isEqualTo(before);
        assertThat(directory).isDirectoryNotContaining("glob:**.tmp");
    }

    private Path bundle(Path file) throws IOException {
        Path media = Files.createDirectories(tmp.resolve("media"));
        return new BundleWriter(Clock.systemUTC()).write(file, Category.CITY,
                List.of(Sign.builder("1", Language.FRENCH, "LAUSANNE").build()), media);
    }
}
