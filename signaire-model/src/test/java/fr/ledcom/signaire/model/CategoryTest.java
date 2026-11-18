package fr.ledcom.signaire.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void hasTheTwentySignSuisseCategories() {
        assertThat(Category.values()).hasSize(20);
    }

    @Test
    void isFoundByItsSignSuisseId() {
        assertThat(Category.fromSignSuisseId(6806)).isEqualTo(Category.NATION);
        assertThat(Category.fromSignSuisseId(6863)).isEqualTo(Category.EVENTS_MEDIA);
    }

    @Test
    void exposesItsSignSuisseId() {
        assertThat(Category.CITY.signSuisseId()).isEqualTo(6809);
    }

    @Test
    void rejectsUnknownIds() {
        assertThatThrownBy(() -> Category.fromSignSuisseId(42))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("42");
    }
}
