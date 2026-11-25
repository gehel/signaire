package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

class SignFormTest {

    private static final Example WRITTEN = new Example("J'habite à Lausanne.", null);
    /** SignSuisse gives some examples as a video only. */
    private static final Example SIGNED_ONLY = new Example(null,
            new Media("https://example.org/example.mp4", "media/example.mp4"));
    private static final Sign LAUSANNE = Sign.builder("1", Language.FRENCH, "LAUSANNE")
            .examples(List.of(WRITTEN, SIGNED_ONLY))
            .build();

    @Test
    void examplesWithoutTextAreNotWrittenInTheForm() {
        assertThat(SignForm.of(LAUSANNE).examples()).isEqualTo("J'habite à Lausanne.");
    }

    @Test
    void examplesWithoutTextAreKeptWhenTheSignIsEdited() {
        assertThat(SignForm.of(LAUSANNE).toSign("1", LAUSANNE).examples()).containsExactly(WRITTEN, SIGNED_ONLY);
    }
}
