package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SearchResults;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/** What every page has: links to SignSuisse, accessibility helpers, its style, exports explained. */
@WebMvcTest(SearchController.class)
@Import({SecurityConfiguration.class, AdminUsers.class})
class PageContentTest {

    @MockitoBean
    private SignSearchService search;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private WordListService lists;

    @Autowired
    private MockMvcTester mvc;

    @BeforeEach
    void oneSign() {
        when(search.search(any())).thenReturn(new SearchResults(1, List.of(
                Sign.builder("119158", Language.FRENCH, "LAUSANNE")
                        .sourceUrl("https://www.sgb-fss.ch/signsuisse/fr/lexikon/119158/lausanne").build())));
    }

    @Test
    void theFooterLinksToSignSuisseInTheUserLanguage() {
        assertThat(mvc.get().uri("/").exchange()).bodyText()
                .contains("href=\"https://www.sgb-fss.ch/signsuisse/fr\"");
        assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange()).bodyText()
                .contains("href=\"https://www.sgb-fss.ch/signsuisse/\"");
        assertThat(mvc.get().uri("/").header(HttpHeaders.ACCEPT_LANGUAGE, "it").exchange()).bodyText()
                .contains("href=\"https://www.sgb-fss.ch/signsuisse/it\"");
    }

    @Test
    void searchResultsLinkToTheirSignSuissePage() {
        assertThat(mvc.get().uri("/").exchange()).bodyText()
                .contains("href=\"https://www.sgb-fss.ch/signsuisse/fr/lexikon/119158/lausanne\"");
    }

    @Test
    void pagesCanBeNavigatedWithTheKeyboard() {
        assertThat(mvc.get().uri("/").exchange()).bodyText()
                .contains("<a class=\"skip\" href=\"#main\">Aller au contenu</a>", "<main id=\"main\"");
    }

    @Test
    void pagesAreStyledByPicoServedFromItsWebjar() throws Exception {
        String page = mvc.get().uri("/").exchange().getResponse().getContentAsString();
        Matcher pico = Pattern.compile(
                "<link rel=\"stylesheet\" href=\"(/webjars/picocss__pico/[^\"]+/pico.red.min.css)\">").matcher(page);
        assertThat(pico.find()).as("link to Pico in %s", page).isTrue();
        assertThat(page.indexOf(pico.group())).as("before our stylesheet")
                .isLessThan(page.indexOf("/css/signaire.css"));
        assertThat(mvc.get().uri(pico.group(1)).exchange()).hasStatus(HttpStatus.OK).hasContentType("text/css");
    }

    @Test
    void pagesPreloadTheirFontServedWithItsLicense() {
        String font = "/fonts/atkinson-hyperlegible-next-latin-wght-normal.woff2";
        assertThat(mvc.get().uri("/").exchange()).bodyText().containsPattern("<link rel=\"preload\" href=\""
                + Pattern.quote(font) + "\" as=\"font\"\\s+type=\"font/woff2\" crossorigin>");
        assertThat(mvc.get().uri(font).exchange()).hasStatus(HttpStatus.OK).hasContentType("font/woff2");
        assertThat(mvc.get().uri("/fonts/OFL.txt").exchange()).hasStatus(HttpStatus.OK).bodyText()
                .contains("SIL Open Font License");
    }

    @Test
    void featuresLinkToTheirHelp() {
        assertThat(mvc.get().uri("/").exchange()).bodyText()
                .contains("href=\"/help\"", "href=\"/help/search\"", "href=\"/help/exports\"");
    }

    @Test
    void exportsAreExplainedAndShowOnlyTheirOptions() {
        assertThat(mvc.get().uri("/").exchange()).bodyText()
                .contains("class=\"format-options pdf-options\"", "class=\"format-options epub-options\"")
                .contains("class=\"format-help anki-help\"", "href=\"https://apps.ankiweb.net\"",
                        "répétition espacée");
    }
}
