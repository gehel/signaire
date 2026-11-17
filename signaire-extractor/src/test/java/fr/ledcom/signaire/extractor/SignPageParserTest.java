package fr.ledcom.signaire.extractor;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

class SignPageParserTest {

    private static final String BASE = "https://www.sgb-fss.ch";
    private static final String VIDEOS = BASE + "/signsuisse/fileadmin/signsuisse_ressources/videos/";
    private static final String IMAGES = BASE + "/signsuisse/fileadmin/signsuisse_ressources/images/";

    private final SignPageParser parser = new SignPageParser();

    @Test
    void parsesAFrenchSign() throws IOException {
        String url = BASE + "/signsuisse/fr/lexikon/1/lausanne";

        Sign sign = parser.parse(fixture("sign-fr.html"), url, "1", Language.FRENCH);

        assertThat(sign.uid()).isEqualTo("1");
        assertThat(sign.language()).isEqualTo(Language.FRENCH);
        assertThat(sign.name()).isEqualTo("LAUSANNE");
        assertThat(sign.sourceUrl()).isEqualTo(url);
        assertThat(sign.periphrase()).isEqualTo("Ville au bord du lac.");
        assertThat(sign.definition()).isEqualTo("Une ville de Suisse romande.");
        assertThat(sign.status()).isEqualTo("Reconnu");
        assertThat(sign.categories()).containsExactly(Category.CITY);
        assertThat(sign.dialects()).isEmpty();
        assertThat(sign.videos()).containsExactly(new Media(VIDEOS + "MAIN-FR.mp4", null));
        assertThat(sign.examples()).containsExactly(
                new Example("Je vais à Lausanne demain.", new Media(VIDEOS + "EXAMPLE-FR.mp4", null)));
        assertThat(sign.images()).containsExactly(new Media(IMAGES + "PHOTO-FR.jpg", null));
        assertThat(sign.dialectVariants()).isEmpty();
    }

    @Test
    void parsesAGermanSignWithDialectVariants() throws IOException {
        Sign sign = parser.parse(fixture("sign-de.html"), BASE + "/signsuisse/lexikon/2/mann", "2", Language.GERMAN);

        assertThat(sign.name()).isEqualTo("MANN");
        assertThat(sign.periphrase()).isEqualTo("eine erwachsene Person");
        assertThat(sign.definition()).isEqualTo("Ein erwachsener Mensch.");
        assertThat(sign.status()).isEqualTo("Anerkannt");
        assertThat(sign.categories()).containsExactly(Category.PEOPLE, Category.FAMILY);
        assertThat(sign.dialects()).containsExactly("BE", "BS", "ZH");
        assertThat(sign.images()).containsExactly(
                new Media(IMAGES + "ILLUSTRATION-DE.jpg", null),
                new Media(IMAGES + "PHOTO-DE.jpg", null));
        assertThat(sign.dialectVariants()).containsExactly(
                new DialectVariant(List.of("BE", "SG"),
                        new Media(BASE + "/fileadmin/signsuisse_ressources/videos/VARIANT-BE-SG.mp4", null)),
                new DialectVariant(List.of("BS"),
                        new Media(BASE + "/fileadmin/signsuisse_ressources/videos/VARIANT-BS.mp4", null)));
    }

    @Test
    void leavesMissingAttributesEmpty() throws IOException {
        Sign sign = parser.parse(fixture("sign-it.html"), BASE + "/signsuisse/it/lexikon/3/lugano", "3",
                Language.ITALIAN);

        assertThat(sign.name()).isEqualTo("LUGANO");
        assertThat(sign.periphrase()).isNull();
        assertThat(sign.definition()).isEqualTo("Una città del Ticino.");
        assertThat(sign.status()).isNull();
        assertThat(sign.categories()).containsExactly(Category.CITY);
        assertThat(sign.dialects()).isEmpty();
        assertThat(sign.examples()).isEmpty();
        assertThat(sign.images()).isEmpty();
    }

    @Test
    void rejectsPagesWithoutASign() {
        String notFound = "<html><body><h1>Lexique de la langue des signes</h1></body></html>";

        assertThatThrownBy(() -> parser.parse(notFound, BASE + "/signsuisse/fr/lexikon/4/x", "4", Language.FRENCH))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("/signsuisse/fr/lexikon/4/x");
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = SignPageParserTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
