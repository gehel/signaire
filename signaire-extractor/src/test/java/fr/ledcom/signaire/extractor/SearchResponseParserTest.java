package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.model.Language;

class SearchResponseParserTest {

    private final SearchResponseParser parser = new SearchResponseParser();

    @Test
    void parsesSearchHits() {
        String json = "{\"items\":["
                + "{\"uid\":\"1\",\"name\":\"LAUSANNE\",\"kategorie\":\"Ville\",\"sprache\":\"fr\","
                + "\"link\":\"https://www.sgb-fss.ch/signsuisse/fr/lexikon/1/lausanne\"},"
                + "{\"uid\":\"2\",\"name\":\"M\\u00c4NNEDORF\",\"kategorie\":\"Stadt\",\"sprache\":\"de\","
                + "\"link\":\"https://www.sgb-fss.ch/signsuisse/lexikon/2/maennedorf\"}"
                + "],\"count\":0}";

        assertThat(parser.parse(json)).containsExactly(
                new SearchHit("1", "LAUSANNE", Language.FRENCH, "https://www.sgb-fss.ch/signsuisse/fr/lexikon/1/lausanne"),
                new SearchHit("2", "MÄNNEDORF", Language.GERMAN, "https://www.sgb-fss.ch/signsuisse/lexikon/2/maennedorf"));
    }

    @Test
    void emptyResultsAreEmpty() {
        assertThat(parser.parse("null")).isEmpty();
        assertThat(parser.parse("{\"items\":null,\"count\":0}")).isEmpty();
        assertThat(parser.parse("{\"items\":[],\"count\":0}")).isEmpty();
    }

    @Test
    void rejectsResponsesThatAreNotJson() {
        assertThatThrownBy(() -> parser.parse("<!DOCTYPE html><html></html>"))
                .isInstanceOf(ExtractionException.class);
    }
}
