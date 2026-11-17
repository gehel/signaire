package fr.ledcom.signaire.bundle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleReaderTest {

    private static final String EMPTY_SIGNS = "[]";
    /** SHA-256 of "[]". */
    private static final String EMPTY_SIGNS_SHA256 = "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945";

    @TempDir
    Path tmp;

    private final BundleReader reader = new BundleReader();

    @Test
    void rejectsBundlesWithoutManifest() throws IOException {
        Path zip = zip("signs.json", EMPTY_SIGNS);

        assertThatThrownBy(() -> reader.read(zip))
                .isInstanceOf(BundleException.class)
                .hasMessageContaining("manifest.json");
    }

    @Test
    void rejectsUnsupportedFormatVersions() throws IOException {
        Path zip = zip("manifest.json", manifest(99, EMPTY_SIGNS_SHA256), "signs.json", EMPTY_SIGNS);

        assertThatThrownBy(() -> reader.read(zip))
                .isInstanceOf(BundleException.class)
                .hasMessageContaining("99");
    }

    @Test
    void rejectsSignsThatDoNotMatchTheirChecksum() throws IOException {
        Path zip = zip("manifest.json", manifest(1, EMPTY_SIGNS_SHA256), "signs.json", "[ ]");

        assertThatThrownBy(() -> reader.read(zip))
                .isInstanceOf(BundleException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void rejectsFilesThatAreNotZips() throws IOException {
        Path notAZip = Files.writeString(tmp.resolve("bundle.zip"), "not a zip");

        assertThatThrownBy(() -> reader.read(notAZip)).isInstanceOf(BundleException.class);
    }

    private static String manifest(int formatVersion, String signsChecksum) {
        return "{\"formatVersion\": " + formatVersion
                + ", \"category\": \"CITY\", \"createdAt\": \"2026-11-17T20:00:00Z\", \"signCount\": 0"
                + ", \"checksums\": {\"signs.json\": \"" + signsChecksum + "\"}}";
    }

    private Path zip(String... namesAndContents) throws IOException {
        Path zip = tmp.resolve("bundle.zip");
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream zipOut = new ZipOutputStream(out)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zipOut.putNextEntry(new ZipEntry(namesAndContents[i]));
                zipOut.write(namesAndContents[i + 1].getBytes(UTF_8));
                zipOut.closeEntry();
            }
        }
        return zip;
    }
}
