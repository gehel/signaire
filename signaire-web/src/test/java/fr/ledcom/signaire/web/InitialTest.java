package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

class InitialTest {

    @Test
    void signsListedByNameAreGroupedUnderTheirInitialWithoutAccents() {
        List<Initial> initials = Initial.group(signs("ARBRE", "ÉCOLE", "EAU", "ÎLE"), true);

        assertThat(initials).extracting(Initial::letter).containsExactly("A", "E", "I");
        assertThat(initials.get(1).signs()).extracting(Sign::name).containsExactly("ÉCOLE", "EAU");
    }

    @Test
    void namesStartingWithAnythingButALetterShareAnInitial() {
        assertThat(Initial.group(signs("1291", "1. AOÛT", "ABEILLE"), true)).extracting(Initial::letter)
                .containsExactly("#", "A");
    }

    @Test
    void signsFoundByWordsHaveNoInitial() {
        List<Initial> initials = Initial.group(signs("GARE", "ARBRE"), false);

        assertThat(initials).singleElement().satisfies(initial -> {
            assertThat(initial.letter()).isNull();
            assertThat(initial.signs()).extracting(Sign::name).containsExactly("GARE", "ARBRE");
        });
    }

    @Test
    void noSignsNoInitials() {
        assertThat(Initial.group(List.of(), true)).isEmpty();
        assertThat(Initial.group(List.of(), false)).isEmpty();
    }

    private static List<Sign> signs(String... names) {
        return Arrays.stream(names).map(name -> Sign.builder(name, Language.FRENCH, name).build()).toList();
    }
}
