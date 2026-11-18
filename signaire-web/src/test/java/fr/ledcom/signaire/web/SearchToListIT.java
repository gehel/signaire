package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.AccountsCleaner;
import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;

@WebIntegrationTest
class SearchToListIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private WordListService lists;

    @Autowired
    private AccountsCleaner cleaner;

    @Autowired
    private SignIndex index;

    private LocalUser alice;
    private WordList family;

    @BeforeEach
    void setUp() {
        cleaner.deleteAll();
        index.recreate();
        index.index(TestSigns.ALL);
        index.refresh();
        accounts.register("alice", "correct horse", "Alice");
        alice = new LocalUser(accounts.findLocalCredentials("alice").orElseThrow(), new AdminUsers(List.of()));
        family = lists.create(alice.accountId(), "Famille");
    }

    @Test
    void resultsCanBeSelectedByLoggedInUsers() {
        MvcTestResult result = mvc.get().uri("/?category=CITY").with(user(alice)).exchange();

        assertThat(result).bodyText()
                .contains("form=\"add-to-list\"", "name=\"uid\"", "value=\"1\"")
                .contains("<option value=\"" + family.id() + "\">Famille</option>")
                .contains("Ajouter la sélection", "Ajouter tous les résultats (2)");
    }

    @Test
    void anonymousUsersCannotSelectResults() {
        assertThat(mvc.get().uri("/?category=CITY").exchange()).bodyText().doesNotContain("add-to-list");
    }

    @Test
    void addsTheSelectedSigns() {
        MvcTestResult result = mvc.post().uri("/lists/signs").with(user(alice)).with(csrf())
                .param("scope", "selection").param("uid", "3", "1").param("list", String.valueOf(family.id()))
                .param("q", "").param("category", "CITY", "ANIMALS")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND)
                .hasRedirectedUrl("/?category=CITY&category=ANIMALS");
        assertThat(result).flash().containsEntry("status", "2 signes ajoutés à « Famille ».");
        assertThat(lists.get(alice.accountId(), family.id()).signUids()).containsExactly("3", "1");
    }

    @Test
    void addsAllTheResultsOfTheSearch() {
        mvc.post().uri("/lists/signs").with(user(alice)).with(csrf())
                .param("scope", "all").param("uid", "3").param("list", String.valueOf(family.id()))
                .param("category", "CITY")
                .exchange();

        assertThat(lists.get(alice.accountId(), family.id()).signUids()).containsExactly("2", "1");
    }

    @Test
    void addsToANewList() {
        MvcTestResult result = mvc.post().uri("/lists/signs").with(user(alice)).with(csrf())
                .header("HX-Request", "true")
                .param("scope", "all").param("list", "").param("newList", "Villes").param("q", "lausanne")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().doesNotContain("<html").contains("1 signe ajouté à « Villes ».");
        assertThat(lists.lists(alice.accountId())).extracting(WordList::name).contains("Villes");
    }

    @Test
    void explainsWhatIsMissing() {
        assertThat(mvc.post().uri("/lists/signs").with(user(alice)).with(csrf()).header("HX-Request", "true")
                .param("scope", "selection").param("list", String.valueOf(family.id())).exchange())
                .bodyText().contains("Cochez d&#39;abord des signes.");
        assertThat(mvc.post().uri("/lists/signs").with(user(alice)).with(csrf()).header("HX-Request", "true")
                .param("scope", "all").param("list", "").param("newList", " ").exchange())
                .bodyText().contains("Le nom doit compter 1 à 100 caractères.");
    }

    @Test
    void requiresLogin() {
        assertThat(mvc.post().uri("/lists/signs").with(csrf()).param("scope", "all").exchange())
                .hasStatus(HttpStatus.FOUND);
    }
}
