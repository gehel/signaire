package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SearchResults;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/** What every page has: links to SignSuisse, accessibility helpers, exports explained. */
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
    void exportsAreExplainedAndShowOnlyTheirOptions() {
        assertThat(mvc.get().uri("/").exchange()).bodyText()
                .contains("class=\"format-options pdf-options\"", "class=\"format-options epub-options\"")
                .contains("class=\"format-help anki-help\"", "href=\"https://apps.ankiweb.net\"",
                        "répétition espacée");
    }
}
