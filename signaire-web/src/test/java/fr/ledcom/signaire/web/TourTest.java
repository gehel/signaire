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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SearchResults;
import fr.ledcom.signaire.core.search.SignSearchService;
import jakarta.servlet.http.Cookie;

@WebMvcTest({SearchController.class, TourController.class})
@Import({SecurityConfiguration.class, AdminUsers.class})
class TourTest {

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
    void newVisitorsAreWelcomed() {
        assertThat(mvc.get().uri("/").exchange()).bodyText()
                .contains("Bienvenue", "href=\"/tour/1\"", "href=\"/tour/done\"");
    }

    @Test
    void theWelcomeCanBeDismissed() {
        MvcTestResult done = mvc.get().uri("/tour/done").exchange();

        assertThat(done).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/");
        Cookie cookie = done.getResponse().getCookie(TourController.COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isPositive();
        assertThat(mvc.get().uri("/").cookie(cookie).exchange()).bodyText().doesNotContain("href=\"/tour/1\"");
    }

    @Test
    void theTourPresentsTheFeaturesStepByStep() {
        MvcTestResult first = mvc.get().uri("/tour/1").header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(first).hasStatus(HttpStatus.OK);
        assertThat(first).bodyText()
                .contains("<html lang=\"de\">", "1 / 5", "href=\"/tour/2\"", "href=\"/help/search\"")
                .doesNotContain("href=\"/tour/0\"");
        assertThat(mvc.get().uri("/tour/5").exchange()).bodyText()
                .contains("5 / 5", "href=\"/tour/4\"", "href=\"/tour/done\"").doesNotContain("href=\"/tour/6\"");
    }

    @Test
    void unknownStepsAreNotFound() {
        assertThat(mvc.get().uri("/tour/6").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }
}
