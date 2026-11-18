package fr.ledcom.signaire.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LanguageTest {

    @Test
    void isFoundByItsSignSuisseCode() {
        assertThat(Language.fromCode("fr")).isEqualTo(Language.FRENCH);
        assertThat(Language.fromCode("DE")).isEqualTo(Language.GERMAN);
        assertThat(Language.fromCode("it")).isEqualTo(Language.ITALIAN);
    }

    @Test
    void exposesItsCode() {
        assertThat(Language.ITALIAN.code()).isEqualTo("it");
    }

    @Test
    void rejectsUnknownCodes() {
        assertThatThrownBy(() -> Language.fromCode("en"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("en");
    }
}
