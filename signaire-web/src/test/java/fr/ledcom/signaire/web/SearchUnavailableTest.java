package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
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
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.core.search.SignSearchService;

// the web layer only: no Elasticsearch, no database
@WebMvcTest(SearchController.class)
@Import(SecurityConfiguration.class)
class SearchUnavailableTest {

    @MockitoBean
    private SignSearchService search;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private WordListService lists;

    @Autowired
    private MockMvcTester mvc;

    @Test
    void searchErrorsAreExplainedInTheUserLanguage() {
        when(search.search(any())).thenThrow(new SearchException("Cannot search signs"));

        MvcTestResult result = mvc.get().uri("/?q=chat").header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyText().contains("<html lang=\"de\">", "Suche nicht verfügbar");
    }

    @Test
    void htmxIsServed() {
        assertThat(mvc.get().uri("/webjars/htmx.org/dist/htmx.min.js").exchange()).hasStatus(HttpStatus.OK);
    }
}
