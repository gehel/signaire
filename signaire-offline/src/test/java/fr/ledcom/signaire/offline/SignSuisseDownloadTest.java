package fr.ledcom.signaire.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import fr.ledcom.signaire.extractor.ExtractionListener;
import fr.ledcom.signaire.extractor.ExtractionOptions;
import fr.ledcom.signaire.extractor.ExtractionReport;
import fr.ledcom.signaire.extractor.FakeSignSource;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.model.TestSigns;

class SignSuisseDownloadTest {

    @TempDir
    Path tmp;

    @Test
    void downloadedCategoriesAreInTheLibraryWithTheirMedia() throws IOException {
        try (SignLibrary library = new SignLibrary(tmp.resolve("bundles"))) {
            SignSuisseDownload download = new SignSuisseDownload(new FakeSignSource(), library, tmp.resolve("cache"),
                    ExtractionListener.NONE);

            List<ExtractionReport> reports = download.download(List.of(Category.CITY), Set.of(Language.FRENCH),
                    ExtractionOptions.of(MediaChoice.ALL));

            assertThat(reports).singleElement().extracting(ExtractionReport::signCount)
                    .isEqualTo(TestSigns.of(Category.CITY, Language.FRENCH).size());
            assertThat(library.bundles()).extracting(BundleInfo::category).containsExactly(Category.CITY);
            Sign gare = library.sign(TestSigns.GARE.uid()).orElseThrow();
            assertThat(library.media(gare.videos().getFirst(), tmp.resolve("shown"))).hasValueSatisfying(
                    video -> assertThat(video).hasContent("content of " + TestSigns.GARE.videos().getFirst().sourceUrl()));
            assertThat(download.status()).isPresent();
        }
    }

    @Test
    void withoutAFileNamingAnotherServerDownloadsAreFromSignSuisse() {
        assertThat(SignSuisseDownload.server(tmp.resolve("signsuisse-server")))
                .isEqualTo(URI.create("https://www.sgb-fss.ch"));
    }

    @Test
    void aFileNamesTheServerOfTheTests() throws IOException {
        Path file = Files.writeString(tmp.resolve("signsuisse-server"), "http://127.0.0.1:4321\n");

        assertThat(SignSuisseDownload.server(file)).isEqualTo(URI.create("http://127.0.0.1:4321"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "127.0.0.1:4321", "file:///sdcard/signs", "http:///signsuisse", "not an address"})
    void aFileNamingNoServerStopsTheDownloadRatherThanGoToSignSuisse(String content) throws IOException {
        Path file = Files.writeString(tmp.resolve("signsuisse-server"), content);

        assertThatThrownBy(() -> SignSuisseDownload.server(file)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString());
    }
}
