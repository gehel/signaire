package fr.ledcom.signaire.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

class SignSuisseDownloadTest {

    @TempDir
    Path tmp;

    @Test
    void downloadedCategoriesAreInTheLibraryWithTheirMedia() throws IOException {
        try (SignLibrary library = new SignLibrary(tmp.resolve("bundles"))) {
            SignSuisseDownload download = new SignSuisseDownload(new FakeSignSuisse(), library, tmp.resolve("cache"),
                    ExtractionListener.NONE);

            List<ExtractionReport> reports = download.download(List.of(Category.CITY), Set.of(Language.FRENCH),
                    ExtractionOptions.of(MediaChoice.ALL));

            assertThat(reports).singleElement().extracting(ExtractionReport::signCount).isEqualTo(1);
            assertThat(library.bundles()).extracting(BundleInfo::category).containsExactly(Category.CITY);
            Sign lausanne = library.sign("1").orElseThrow();
            assertThat(library.media(lausanne.videos().getFirst(), tmp.resolve("shown"))).hasValueSatisfying(
                    video -> assertThat(video).hasContent("video"));
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

    /** One sign, LAUSANNE, with a video. */
    private static final class FakeSignSuisse implements SignSource {

        @Override
        public List<SearchHit> search(String term, Language language, Category category) {
            return category == Category.CITY && language == Language.FRENCH && "lausanne".startsWith(term)
                    ? List.of(new SearchHit("1", "LAUSANNE", language, "https://example.org/1"))
                    : List.of();
        }

        @Override
        public Sign fetchSign(SearchHit hit) {
            return Sign.builder(hit.uid(), hit.language(), hit.name()).categories(List.of(Category.CITY))
                    .videos(List.of(new Media("https://example.org/lausanne.mp4", null))).build();
        }

        @Override
        public void download(URI uri, Path target) {
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, "video");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
