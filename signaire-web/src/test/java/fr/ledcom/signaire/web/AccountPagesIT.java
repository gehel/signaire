package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.AccountsCleaner;
import fr.ledcom.signaire.core.search.SignIndex;

@WebIntegrationTest
class AccountPagesIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private AccountsCleaner cleaner;

    @Autowired
    private SignIndex index;

    @BeforeEach
    void clean() {
        cleaner.deleteAll();
        index.recreate();
    }

    @Test
    void loginPageIsInTheUserLanguage() {
        MvcTestResult result = mvc.get().uri("/login").header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().contains("<h1>Anmelden</h1>", "name=\"username\"", "name=\"password\"",
                "name=\"_csrf\"", "href=\"/register\"");
    }

    @Test
    void registeredUsersCanLogIn() {
        MvcTestResult registered = mvc.post().uri("/register").with(csrf())
                .param("username", "Alice").param("displayName", "Alice A.")
                .param("password", "correct horse").param("confirmation", "correct horse")
                .exchange();

        assertThat(registered).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/login?registered");
        assertThat(mvc.get().uri("/login?registered").exchange()).bodyText().contains("Votre compte est créé");

        MvcTestResult login = mvc.perform(formLogin().user("alice").password("correct horse"));

        assertThat(login).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/");
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession();
        assertThat(mvc.get().uri("/").session(session).exchange()).bodyText()
                .contains("Alice A.", "href=\"/lists\"", "action=\"/logout\"");
    }

    @Test
    void wrongPasswordsAreRejected() {
        accounts.register("alice", "correct horse", null);

        MvcTestResult login = mvc.perform(formLogin().user("alice").password("wrong password"));

        assertThat(login).hasRedirectedUrl("/login?error");
        assertThat(mvc.get().uri("/login?error").exchange()).bodyText()
                .contains("Nom d&#39;utilisateur ou mot de passe incorrect.");
    }

    @Test
    void explainsWhyAnAccountCannotBeCreated() {
        accounts.register("alice", "correct horse", null);

        assertThat(register("alice", "correct horse", "correct horse")).bodyText()
                .contains("Ce nom d&#39;utilisateur est déjà pris.")
                .contains("value=\"alice\"");
        assertThat(register("al", "correct horse", "correct horse")).bodyText()
                .contains("Le nom d&#39;utilisateur doit compter 3 à 40");
        assertThat(register("bob", "short", "short")).bodyText().contains("au moins 8 caractères");
        assertThat(register("bob", "correct horse", "correct horsse")).bodyText()
                .contains("Les deux mots de passe ne sont pas identiques.");
    }

    @Test
    void anonymousUsersSeeLoginLinks() {
        assertThat(mvc.get().uri("/").exchange()).bodyText().contains("href=\"/login\"").doesNotContain("/logout");
    }

    @Test
    void listsRequireLogin() {
        MvcTestResult result = mvc.get().uri("/lists").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login");
    }

    private MvcTestResult register(String username, String password, String confirmation) {
        MvcTestResult result = mvc.post().uri("/register").with(csrf())
                .param("username", username).param("displayName", "")
                .param("password", password).param("confirmation", confirmation)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return result;
    }
}
