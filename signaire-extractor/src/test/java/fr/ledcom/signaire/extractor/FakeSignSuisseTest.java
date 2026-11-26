package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.model.TestSigns;

/** The fake SignSuisse is read by the client as SignSuisse is: if its pages drifted, the parser would say so. */
class FakeSignSuisseTest {

    private final FakeSignSuisse signSuisse = new FakeSignSuisse();
    private final SignSuisseClient client = new SignSuisseClient(
            new ThrottledHttpFetcher(Duration.ZERO, 1, UserAgent.of("", "")), signSuisse.uri());

    FakeSignSuisseTest() throws IOException {
    }

    @AfterEach
    void close() {
        signSuisse.close();
    }

    @Test
    void everyPageIsReadBackAsItsSign() {
        for (Sign sign : TestSigns.LEXICON) {
            SearchHit hit = new SearchHit(sign.uid(), sign.name(), sign.language(), signSuisse.link(sign));

            assertThat(client.fetchSign(hit)).as(sign.name()).isEqualTo(signSuisse.served(sign));
        }
    }

    @Test
    void searchesAsSignSuisseNamesStartingWithTheTermFirst() {
        assertThat(client.search("g", Language.FRENCH, null)).extracting(SearchHit::name)
                .containsExactly("GARE", "GENÈVE", "MONTAGNE");
        assertThat(client.search("g", Language.FRENCH, Category.CITY)).extracting(SearchHit::name)
                .containsExactly("GARE", "GENÈVE");
        assertThat(client.search("zur", Language.GERMAN, Category.CITY)).extracting(SearchHit::url)
                .containsExactly(signSuisse.link(TestSigns.ZUERICH));
        assertThat(client.search("zur", Language.FRENCH, Category.CITY)).isEmpty();
    }

    @Test
    void theEnumerationOfACategoryFindsAllItsSigns() {
        for (Language language : Language.values()) {
            assertThat(SignEnumerator.forSignSuisse(client).enumerate(language, Category.NATURE).hits())
                    .extracting(SearchHit::uid)
                    .containsExactlyInAnyOrderElementsOf(TestSigns.of(Category.NATURE, language).stream()
                            .map(Sign::uid).toList());
        }
    }

    @Test
    void servesTheMediaOfThePages(@TempDir Path tmp) throws IOException {
        Sign gare = signSuisse.served(TestSigns.GARE);
        URI video = URI.create(gare.videos().getFirst().sourceUrl());
        URI photo = URI.create(gare.images().getFirst().sourceUrl());

        client.download(video, tmp.resolve("gare.mp4"));
        client.download(photo, tmp.resolve("gare.jpg"));

        assertThat(Files.readString(tmp.resolve("gare.mp4"))).startsWith("fake video");
        assertThat(tmp.resolve("gare.jpg")).isNotEmptyFile();
        assertThat(signSuisse.requests()).containsExactly(video.getPath(), photo.getPath());
    }

    @Test
    void failsAsSignSuisseOnTheSearchesItDoesNotAnswer() {
        assertThatThrownBy(() -> client.search("0", Language.FRENCH, null)).isInstanceOf(ExtractionException.class);
    }
}
