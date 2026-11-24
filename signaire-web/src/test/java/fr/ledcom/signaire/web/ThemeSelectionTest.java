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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SearchResults;
import fr.ledcom.signaire.core.search.SignSearchService;
import jakarta.servlet.http.Cookie;

// the web layer only: no index, no database
@WebMvcTest(SearchController.class)
@Import({SecurityConfiguration.class, AdminUsers.class})
class ThemeSelectionTest {

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
    void pagesFollowTheThemeOfTheSystemByDefault() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).hasStatus(HttpStatus.OK).bodyText()
                .contains("<html lang=\"fr\">")
                .containsPattern("data-choice=\"auto\"[^>]*aria-current=\"true\"");
    }

    @Test
    void themeChosenByTheUserIsRememberedInACookie() {
        MvcTestResult chosen = mvc.get().uri("/?theme=dark").exchange();

        assertThat(chosen).bodyText().contains("<html lang=\"fr\" data-theme=\"dark\">")
                .containsPattern("data-choice=\"dark\"[^>]*aria-current=\"true\"");
        assertThat(chosen).cookies().containsCookie(ThemeAdvice.COOKIE);

        Cookie cookie = chosen.getResponse().getCookie(ThemeAdvice.COOKIE);
        assertThat(mvc.get().uri("/").cookie(cookie).exchange()).bodyText()
                .contains("<html lang=\"fr\" data-theme=\"dark\">");
    }

    @Test
    void automaticForgetsTheChoice() {
        MvcTestResult result = mvc.get().uri("/?theme=auto").cookie(new Cookie(ThemeAdvice.COOKIE, "light")).exchange();

        assertThat(result).bodyText().contains("<html lang=\"fr\">");
        assertThat(result).cookies().hasCookieSatisfying(ThemeAdvice.COOKIE,
                cookie -> assertThat(cookie.getMaxAge()).isZero());
    }

    @Test
    void unknownThemesAreIgnored() {
        MvcTestResult result = mvc.get().uri("/?theme=pink").cookie(new Cookie(ThemeAdvice.COOKIE, "blue")).exchange();

        assertThat(result).hasStatus(HttpStatus.OK).bodyText().contains("<html lang=\"fr\">");
        assertThat(result).cookies().doesNotContainCookie(ThemeAdvice.COOKIE);
    }

    @Test
    void themeLinksKeepTheCurrentSearch() {
        MvcTestResult result = mvc.get().uri("/?q=chat&lang=de").exchange();

        assertThat(result).bodyText().contains("href=\"/?q=chat&amp;theme=dark\"", ">Dunkel<")
                .contains("href=\"/?q=chat&amp;lang=it\"");
    }

    @Test
    void searchesAsYouTypeRefreshTheThemeLinks() {
        MvcTestResult result = mvc.get().uri("/?q=chat").header("HX-Request", "true").exchange();

        assertThat(result).bodyText().containsPattern("id=\"themes\"[^>]*hx-swap-oob=\"true\"")
                .contains("href=\"/?q=chat&amp;theme=light\"");
    }
}
