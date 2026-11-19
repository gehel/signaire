package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;

@WebLayerTest(SearchController.class)
class SearchPagesTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private SignIndex index;

    @BeforeEach
    void indexSigns() {
        index.recreate();
        index.index(TestSigns.ALL);
        index.refresh();
    }

    @Test
    void findsSignsAndLinksToThem() {
        MvcTestResult result = mvc.get().uri("/?q=lausanne").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText()
                .contains("<html lang=\"fr\">")
                .contains("1 signe trouvé")
                .contains("href=\"/signs/1\"", "LAUSANNE")
                .contains("value=\"lausanne\"")
                .doesNotContain("GENÈVE");
    }

    @Test
    void listsAllSignsByNameWithoutWords() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).bodyText().contains("5 signes trouvés", "CHAT", "GATTO").doesNotContain("KATZE");
    }

    @Test
    void pagesThroughTheResults() {
        MvcTestResult result = mvc.get().uri("/?page=2").exchange();

        assertThat(result).bodyText()
                .contains("Page 2 sur 3")
                .contains("GENÈVE", "KATZE")
                .contains("href=\"/?page=1\"", "href=\"/?page=3\"")
                .doesNotContain("GATTO");
    }

    @Test
    void filtersBySignLanguage() {
        MvcTestResult result = mvc.get().uri("/?language=de").exchange();

        assertThat(result).bodyText().contains("1 signe trouvé", "KATZE").doesNotContain("CHAT");
    }

    @Test
    void filtersByCategory() {
        MvcTestResult result = mvc.get().uri("/?category=CITY").exchange();

        assertThat(result).bodyText().contains("2 signes trouvés", "GENÈVE", "LAUSANNE");
    }

    @Test
    void showsLanguagesAndCategoriesInTheUserLanguage() {
        MvcTestResult result = mvc.get().uri("/?q=lausanne").header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(result).bodyText()
                .contains("1 Gebärde gefunden")
                .contains("title=\"Französische Gebärdensprache (LSF)\">LSF<")
                .contains("class=\"categories\">Stadt<");
    }

    @Test
    void saysWhenNothingIsFound() {
        MvcTestResult result = mvc.get().uri("/?q=xyzzy").exchange();

        assertThat(result).bodyText().contains("Aucun signe trouvé.");
    }

    @Test
    void htmxRequestsGetTheResultsOnly() {
        MvcTestResult result = mvc.get().uri("/?q=chat").header("HX-Request", "true").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).hasHeader(HttpHeaders.VARY, "HX-Request");
        assertThat(result).bodyText()
                .doesNotContain("<html")
                .contains("id=\"results\"", "CHAT")
                .contains("hx-swap-oob");
    }

    @Test
    void htmxHistoryRestorationGetsTheWholePage() {
        MvcTestResult result = mvc.get().uri("/?q=chat").header("HX-Request", "true")
                .header("HX-History-Restore-Request", "true").exchange();

        assertThat(result).bodyText().contains("<html");
    }

    @Test
    void rejectsUnknownSignLanguages() {
        assertThat(mvc.get().uri("/?language=xx").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
