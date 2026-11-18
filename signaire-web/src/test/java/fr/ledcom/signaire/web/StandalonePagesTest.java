package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import fr.ledcom.signaire.accounts.Account;
import fr.ledcom.signaire.accounts.AccountException;
import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.search.SearchResults;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

// the web layer only: no index, no database
@WebMvcTest({SetupController.class, SearchController.class})
@Import({SecurityConfiguration.class, AdminUsers.class})
class StandalonePagesTest {

    @MockitoBean
    private Standalone standalone;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private SignSuisseSync sync;

    @MockitoBean
    private SignSearchService search;

    @MockitoBean
    private WordListService lists;

    @Autowired
    private AdminUsers admins;

    @Autowired
    private MockMvcTester mvc;

    @BeforeEach
    void firstRun() {
        when(standalone.enabled()).thenReturn(true);
        when(standalone.needsSetup()).thenReturn(true);
        when(search.search(any())).thenReturn(new SearchResults(0, List.of()));
    }

    @Test
    void theFirstRunStartsWithTheSetup() {
        assertThat(mvc.get().uri("/").exchange()).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/setup");
        // before the login, too
        assertThat(mvc.get().uri("/lists").exchange()).hasRedirectedUrl("/setup");
        assertThat(mvc.get().uri("/setup").exchange()).hasStatus(HttpStatus.OK).bodyText()
                .contains("Bienvenue dans Signaire", "name=\"alone\"", "value=\"CITY\"", "href=\"/help/standalone\"");
    }

    @Test
    void theSetupProposesTheSignLanguageOfThePage() {
        assertThat(mvc.get().uri("/setup?lang=de").exchange()).bodyText()
                .containsPattern("value=\"de\"\\s+checked");
    }

    @Test
    void aloneTheDownloadStartsAtOnce() {
        assertThat(mvc.post().uri("/setup").with(csrf()).param("alone", "true")
                .param("categories", "CITY").param("languages", "fr").exchange())
                .hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/admin/sync");

        verify(standalone).completeSetup(true, null);
        verify(sync).start(List.of(Category.CITY), List.of(Language.FRENCH), false);
    }

    @Test
    void theDownloadCanWait() {
        assertThat(mvc.post().uri("/setup").with(csrf()).param("alone", "true").exchange()).hasRedirectedUrl("/");

        verify(sync, never()).start(any(), any(), anyBoolean());
    }

    @Test
    void severalUsersStartWithAnAdministrator() {
        when(accounts.register("alice", "correct horse", "Alice")).thenReturn(new Account(1, "alice", "Alice"));

        assertThat(mvc.post().uri("/setup").with(csrf()).param("alone", "false").param("username", "alice")
                .param("displayName", "Alice").param("password", "correct horse")
                .param("confirmation", "correct horse").exchange()).hasRedirectedUrl("/login?registered");

        verify(standalone).completeSetup(false, "alice");
        assertThat(admins.isAdmin("alice")).isTrue();
    }

    @Test
    void theAdministratorAccountMustBeValid() {
        assertThat(mvc.post().uri("/setup").with(csrf()).param("alone", "false").param("username", "alice")
                .param("password", "correct horse").param("confirmation", "wrong horse").exchange())
                .hasStatus(HttpStatus.OK).bodyText().contains("Les deux mots de passe ne sont pas identiques.");

        when(accounts.register(anyString(), anyString(), any()))
                .thenThrow(new AccountException(AccountException.Reason.USERNAME_TAKEN, "taken"));
        assertThat(mvc.post().uri("/setup").with(csrf()).param("alone", "false").param("username", "alice")
                .param("password", "correct horse").param("confirmation", "correct horse").exchange())
                .bodyText().contains("Ce nom d&#39;utilisateur est déjà pris.");

        verify(standalone, never()).completeSetup(anyBoolean(), any());
    }

    @Test
    void theSetupIsDoneOnce() {
        when(standalone.needsSetup()).thenReturn(false);

        assertThat(mvc.get().uri("/setup").exchange()).hasRedirectedUrl("/");
        assertThat(mvc.post().uri("/setup").with(csrf()).param("alone", "true").exchange()).hasRedirectedUrl("/");
        verify(standalone, never()).completeSetup(anyBoolean(), any());
    }

    @Test
    void onlyFromThisComputer() {
        assertThat(mvc.get().uri("/setup").with(request -> {
            request.setRemoteAddr("192.168.1.20");
            return request;
        }).exchange()).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void serversHaveNoSetup() {
        when(standalone.enabled()).thenReturn(false);
        when(standalone.needsSetup()).thenReturn(false);

        assertThat(mvc.get().uri("/setup").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theSingleUserCanQuitButNotLogOut() {
        when(standalone.needsSetup()).thenReturn(false);
        when(standalone.singleUser()).thenReturn(true);
        when(accounts.findOrCreateOidc(any(), any(), any(), any())).thenReturn(new Account(1, "signaire", "Signaire"));

        assertThat(mvc.get().uri("/").exchange()).bodyText()
                .contains("Quitter Signaire", "href=\"/admin\"", "href=\"/lists\"")
                .doesNotContain("Se déconnecter", "Se connecter");
    }
}
