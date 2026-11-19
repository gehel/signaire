package fr.ledcom.signaire.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SignTest {

    private static final Media VIDEO = new Media(
            "https://www.sgb-fss.ch/signsuisse/fileadmin/signsuisse_ressources/videos/F812.mp4", "media/F812.mp4");

    @Test
    void keepsAllItsAttributes() {
        Example example = new Example("Lausanne est la capitale du canton de Vaud.", VIDEO);
        DialectVariant variant = new DialectVariant(List.of("BE", "SG"), VIDEO);
        Sign sign = Sign.builder("119158", Language.FRENCH, "LAUSANNE")
                .sourceUrl("https://www.sgb-fss.ch/signsuisse/fr/lexikon/119158/lausanne")
                .periphrase("Ville, lac, colline.")
                .definition("Lausanne est une ville de Suisse.")
                .status("Reconnu")
                .categories(List.of(Category.CITY))
                .dialects(List.of("BE", "SG"))
                .videos(List.of(VIDEO))
                .examples(List.of(example))
                .dialectVariants(List.of(variant))
                .build();

        assertThat(sign.uid()).isEqualTo("119158");
        assertThat(sign.language()).isEqualTo(Language.FRENCH);
        assertThat(sign.name()).isEqualTo("LAUSANNE");
        assertThat(sign.sourceUrl()).isEqualTo("https://www.sgb-fss.ch/signsuisse/fr/lexikon/119158/lausanne");
        assertThat(sign.periphrase()).isEqualTo("Ville, lac, colline.");
        assertThat(sign.definition()).isEqualTo("Lausanne est une ville de Suisse.");
        assertThat(sign.status()).isEqualTo("Reconnu");
        assertThat(sign.dialects()).containsExactly("BE", "SG");
        assertThat(sign.dialectVariants()).containsExactly(variant);
        assertThat(sign.categories()).containsExactly(Category.CITY);
        assertThat(sign.videos()).containsExactly(VIDEO);
        assertThat(sign.examples()).containsExactly(example);
        assertThat(sign.images()).isEmpty();
    }

    @Test
    void requiresUidLanguageAndName() {
        assertThatThrownBy(() -> minimalSign(null, Language.FRENCH, "X")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> minimalSign("1", null, "X")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> minimalSign("1", Language.FRENCH, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void treatsMissingListsAsEmpty() {
        Sign sign = minimalSign("1", Language.FRENCH, "X");

        assertThat(sign.categories()).isEmpty();
        assertThat(sign.videos()).isEmpty();
        assertThat(sign.examples()).isEmpty();
        assertThat(sign.images()).isEmpty();
        assertThat(sign.dialects()).isEmpty();
        assertThat(sign.dialectVariants()).isEmpty();
    }

    @Test
    void isNotAffectedByChangesToTheListsItWasBuiltFrom() {
        List<Category> categories = new ArrayList<>(List.of(Category.CITY));
        Sign sign = Sign.builder("1", Language.FRENCH, "X").categories(categories).build();

        categories.add(Category.NATION);

        List<Category> signCategories = sign.categories();
        assertThat(signCategories).containsExactly(Category.CITY);
        assertThatThrownBy(() -> signCategories.add(Category.NATION))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void canBeCopiedThroughABuilder() {
        Sign sign = Sign.builder("1", Language.GERMAN, "MANN").definition("Ein Mann").build();

        Sign copy = sign.toBuilder().status("Anerkannt").build();

        assertThat(copy.definition()).isEqualTo("Ein Mann");
        assertThat(copy.status()).isEqualTo("Anerkannt");
        assertThat(sign.status()).isNull();
    }

    @Test
    void mediaRequiresASourceUrlButNotALocalFile() {
        assertThat(new Media("https://example.org/a.jpg", null).file()).isNull();
        assertThatThrownBy(() -> new Media(null, "media/a.jpg")).isInstanceOf(NullPointerException.class);
    }

    private static Sign minimalSign(String uid, Language language, String name) {
        return Sign.builder(uid, language, name).build();
    }
}
