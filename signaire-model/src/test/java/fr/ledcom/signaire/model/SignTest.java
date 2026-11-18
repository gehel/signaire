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
        Sign sign = new Sign("119158", Language.FRENCH, "LAUSANNE",
                "https://www.sgb-fss.ch/signsuisse/fr/lexikon/119158/lausanne",
                "Ville, lac, colline.", "Lausanne est une ville de Suisse.", "Reconnu",
                List.of(Category.CITY), List.of(VIDEO), List.of(example), List.of());

        assertThat(sign.uid()).isEqualTo("119158");
        assertThat(sign.language()).isEqualTo(Language.FRENCH);
        assertThat(sign.name()).isEqualTo("LAUSANNE");
        assertThat(sign.periphrase()).isEqualTo("Ville, lac, colline.");
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
    }

    @Test
    void isNotAffectedByChangesToTheListsItWasBuiltFrom() {
        List<Category> categories = new ArrayList<>(List.of(Category.CITY));
        Sign sign = new Sign("1", Language.FRENCH, "X", null, null, null, null, categories, null, null, null);

        categories.add(Category.NATION);

        assertThat(sign.categories()).containsExactly(Category.CITY);
        assertThatThrownBy(() -> sign.categories().add(Category.NATION))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mediaRequiresASourceUrlButNotALocalFile() {
        assertThat(new Media("https://example.org/a.jpg", null).file()).isNull();
        assertThatThrownBy(() -> new Media(null, "media/a.jpg")).isInstanceOf(NullPointerException.class);
    }

    private static Sign minimalSign(String uid, Language language, String name) {
        return new Sign(uid, language, name, null, null, null, null, null, null, null, null);
    }
}
