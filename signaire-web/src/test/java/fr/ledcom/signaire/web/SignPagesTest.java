package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.StringUtils;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.model.TestSigns;
import fr.ledcom.signaire.core.media.MediaLibrary;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

@WebLayerTest({SignController.class, MediaController.class})
class SignPagesTest {

    /** A sign whose media were not downloaded, with everything a sign can have. */
    private static final Sign BAER = Sign.builder("10", Language.GERMAN, "BÄR")
            .sourceUrl("https://www.sgb-fss.ch/signsuisse/lexikon/10/baer")
            .periphrase("Grosses Raubtier")
            .categories(List.of(Category.ANIMALS, Category.NATURE))
            .dialects(List.of("BE"))
            .videos(List.of(new Media("https://example.org/baer.mp4", null)))
            .images(List.of(new Media("https://example.org/baer.jpg", null)))
            .examples(List.of(new Example("Der Bär schläft.", new Media("https://example.org/baer-1.mp4", null))))
            .dialectVariants(List.of(new DialectVariant(List.of("ZH", "SG"),
                    new Media("https://example.org/baer-zh.mp4", null))))
            .build();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private SignIndex index;

    @Autowired
    private MediaLibrary media;

    @Value("${signaire.bundles.directory}")
    private Path bundles;

    @BeforeEach
    void indexSigns() throws IOException {
        index.recreate();
        index.index(List.of(TestSigns.LAUSANNE, TestSigns.KATZE, BAER));
        index.refresh();
        Path mediaRoot = Files.createDirectories(bundles.resolve(".media/media"));
        Files.writeString(mediaRoot.resolve("lausanne.mp4"), "video");
        new BundleWriter(Clock.systemUTC()).write(bundles.resolve("city.zip"), Category.CITY,
                List.of(TestSigns.LAUSANNE), bundles.resolve(".media"));
        media.refresh();
    }

    @Test
    void showsTheSign() {
        MvcTestResult result = mvc.get().uri("/signs/1").exchange();

        assertThat(result).hasStatus(HttpStatus.OK)
                .bodyText()
                .contains("<title>LAUSANNE – Signaire</title>")
                .contains("<h1>LAUSANNE")
                .contains("Ville au bord du lac", "Capitale du canton de Vaud", "Je travaille à Lausanne.")
                .contains("href=\"/?category=CITY\">Ville</a>")
                .contains("<video", "src=\"/media/lausanne.mp4\"");
    }

    @Test
    void showsTheSignInTheUserLanguage() {
        MvcTestResult result = mvc.get().uri("/signs/1").header(HttpHeaders.ACCEPT_LANGUAGE, "it").exchange();

        assertThat(result).bodyText().contains("Definizione", "Esempi", ">Città</a>");
    }

    @Test
    void videosPlayOnceAndAgainInSlowMotion() {
        MvcTestResult result = mvc.get().uri("/signs/10").exchange();

        assertThat(result).bodyText()
                .doesNotContainPattern("<video[^>]*\\sloop")
                .contains("src=\"/js/videos.js\"")
                // under each video, the sign's, its example's and its variant's; the script shows it
                .satisfies(page -> assertThat(StringUtils.countOccurrencesOf(page,
                        "<button type=\"button\" class=\"slow small secondary outline\" hidden>"
                                + "Ralenti</button>")).isEqualTo(3));
    }

    @Test
    void playsTheMediaThatWereNotDownloadedFromSignSuisse() {
        MvcTestResult result = mvc.get().uri("/signs/10").exchange();

        assertThat(result).bodyText()
                .contains("src=\"https://example.org/baer.mp4\"")
                .contains("src=\"https://example.org/baer.jpg\"")
                .contains("src=\"https://example.org/baer-1.mp4\"")
                .contains("src=\"https://example.org/baer-zh.mp4\"");
    }

    @Test
    void showsDialectsAndVariantsAndLinksToSignSuisse() {
        MvcTestResult result = mvc.get().uri("/signs/10").header(HttpHeaders.ACCEPT_LANGUAGE, "en").exchange();

        assertThat(result).bodyText()
                .contains("Dialects", "BE")
                .contains("Regional variants", "ZH, SG")
                .contains("href=\"https://www.sgb-fss.ch/signsuisse/lexikon/10/baer\"", "See on SignSuisse")
                .contains(">Animals</a>", ">Nature</a>");
    }

    @Test
    void textsOfASignAreInItsLanguageAndItsExamplesQuoted() {
        MvcTestResult result = mvc.get().uri("/signs/10").exchange();

        assertThat(result).bodyText()
                .containsPattern("<dd\\s+lang=\"de\">Grosses Raubtier</dd>")
                .containsPattern("<q\\s+lang=\"de\">Der Bär schläft.</q>");
    }

    @Test
    void unknownSignsAreNotFound() {
        MvcTestResult result = mvc.get().uri("/signs/999").header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                .bodyText().contains("Gebärde nicht gefunden");
    }

    @Test
    void servesTheMediaOfTheBundles() {
        MvcTestResult result = mvc.get().uri("/media/lausanne.mp4").exchange();

        assertThat(result).hasStatus(HttpStatus.OK)
                .hasContentType("video/mp4")
                .hasHeader(HttpHeaders.ACCEPT_RANGES, "bytes")
                .hasHeader(HttpHeaders.CACHE_CONTROL, "max-age=604800")
                .bodyText().isEqualTo("video");
    }

    @Test
    void servesRangesOfMedia() {
        MvcTestResult result = mvc.get().uri("/media/lausanne.mp4").header(HttpHeaders.RANGE, "bytes=1-2").exchange();

        assertThat(result).hasStatus(HttpStatus.PARTIAL_CONTENT)
                .bodyText().isEqualTo("id");
    }

    @Test
    void unknownMediaAreNotFound() {
        assertThat(mvc.get().uri("/media/unknown.mp4").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }
}
