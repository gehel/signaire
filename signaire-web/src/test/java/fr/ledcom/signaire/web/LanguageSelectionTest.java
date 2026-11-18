package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SearchResults;
import fr.ledcom.signaire.core.search.SignSearchService;
import jakarta.servlet.http.Cookie;

// the web layer only: no Elasticsearch, no database
@WebMvcTest(SearchController.class)
@Import({SecurityConfiguration.class, AdminUsers.class})
class LanguageSelectionTest {

    @MockitoBean
    private SignSearchService search;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private WordListService lists;

    @Autowired
    private MockMvcTester mvc;

    @BeforeEach
    void noSigns() {
        when(search.search(any())).thenReturn(new SearchResults(0, List.of()));
    }

    @Test
    void homePageIsInFrenchByDefault() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText()
                .contains("<html lang=\"fr\">")
                .contains("<title>Signaire</title>")
                .contains("Lexique de la langue des signes suisse");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            de-CH | de | Lexikon der Schweizer Gebärdensprache
            it    | it | Lessico della lingua dei segni svizzera
            en-GB | en | Swiss sign language lexicon
            """)
    void homePageFollowsTheBrowserLanguage(String acceptLanguage, String language, String tagline) {
        MvcTestResult result = mvc.get().uri("/").header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().contains("<html lang=\"" + language + "\">").contains(tagline);
    }

    @Test
    void unsupportedLanguagesFallBackToFrench() {
        MvcTestResult result = mvc.get().uri("/").header(HttpHeaders.ACCEPT_LANGUAGE, "es").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().contains("<html lang=\"fr\">");
    }

    @Test
    void languageChosenByTheUserIsRememberedInACookie() {
        MvcTestResult chosen = mvc.get().uri("/?lang=it").header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(chosen).bodyText().contains("<html lang=\"it\">");
        assertThat(chosen).cookies().containsCookie(LocaleConfiguration.COOKIE);

        Cookie cookie = chosen.getResponse().getCookie(LocaleConfiguration.COOKIE);
        MvcTestResult next = mvc.get().uri("/").cookie(cookie).header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(next).bodyText().contains("<html lang=\"it\">");
    }

    @Test
    void unsupportedLanguagesCannotBeChosen() {
        MvcTestResult result = mvc.get().uri("/?lang=es").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().contains("<html lang=\"fr\">");
    }

    @Test
    void pagesLinkToEveryLanguage() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).bodyText()
                .contains("hreflang=\"de\"", "Deutsch")
                .contains("hreflang=\"it\"", "Italiano")
                .contains("hreflang=\"en\"", "English")
                .contains("hreflang=\"fr\"", "Français");
    }

    @Test
    void languageLinksKeepTheCurrentSearch() {
        MvcTestResult result = mvc.get().uri("/?q=chat&language=fr").exchange();

        assertThat(result).bodyText().contains("href=\"/?q=chat&amp;language=fr&amp;lang=de\"");
    }
}
