package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.AccountsCleaner;
import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;

@WebIntegrationTest
class ListPagesIT {

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
    private LocalUser bob;

    @BeforeEach
    void setUp() {
        cleaner.deleteAll();
        index.recreate();
        index.index(TestSigns.ALL);
        index.refresh();
        accounts.register("alice", "correct horse", "Alice");
        accounts.register("bob", "correct horse", "Bob");
        alice = new LocalUser(accounts.findLocalCredentials("alice").orElseThrow());
        bob = new LocalUser(accounts.findLocalCredentials("bob").orElseThrow());
    }

    @Test
    void showsTheListsOfTheUser() {
        WordList family = lists.create(alice.accountId(), "Famille");
        lists.add(alice.accountId(), family.id(), TestSigns.CHAT.uid());
        lists.add(alice.accountId(), family.id(), TestSigns.LAUSANNE.uid());
        lists.create(bob.accountId(), "Sport");

        MvcTestResult result = mvc.get().uri("/lists").with(as(alice)).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText()
                .contains("href=\"/lists/" + family.id() + "\">Famille</a>", "2 signes")
                .doesNotContain("Sport");
    }

    @Test
    void saysWhenTheUserHasNoList() {
        assertThat(mvc.get().uri("/lists").with(as(alice)).exchange()).bodyText()
                .contains("Vous n&#39;avez pas encore de liste.");
    }

    @Test
    void createsLists() {
        MvcTestResult result = mvc.post().uri("/lists").with(as(alice)).with(csrf()).param("name", "Famille").exchange();

        WordList created = lists.lists(alice.accountId()).getFirst();
        assertThat(created.name()).isEqualTo("Famille");
        assertThat(result).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/lists/" + created.id());
    }

    @Test
    void explainsWhyAListCannotBeCreated() {
        lists.create(alice.accountId(), "Famille");

        assertThat(mvc.post().uri("/lists").with(as(alice)).with(csrf()).param("name", "famille").exchange())
                .bodyText().contains("Vous avez déjà une liste de ce nom.");
        assertThat(mvc.post().uri("/lists").with(as(alice)).with(csrf()).param("name", " ").exchange())
                .bodyText().contains("Le nom doit compter 1 à 100 caractères.");
    }

    @Test
    void showsTheSignsOfAList() throws UnsupportedEncodingException {
        WordList list = lists.create(alice.accountId(), "Famille");
        lists.add(alice.accountId(), list.id(), TestSigns.LAUSANNE.uid());
        lists.add(alice.accountId(), list.id(), TestSigns.CHAT.uid());

        MvcTestResult result = mvc.get().uri("/lists/" + list.id()).with(as(alice))
                .header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText()
                .contains("<h1>Famille</h1>", "2 Gebärden")
                .contains("href=\"/signs/1\">LAUSANNE</a>", "href=\"/signs/3\">CHAT</a>")
                .contains("action=\"/lists/" + list.id() + "/signs/1/remove\"");
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body.indexOf("LAUSANNE")).isLessThan(body.indexOf("CHAT"));
    }

    @Test
    void mentionsSignsThatAreNoLongerInTheLexicon() {
        WordList list = lists.create(alice.accountId(), "Famille");
        lists.add(alice.accountId(), list.id(), "999999");

        assertThat(mvc.get().uri("/lists/" + list.id()).with(as(alice)).exchange()).bodyText()
                .contains("Signes absents du lexique : 1.");
    }

    @Test
    void renamesRemovesAndDeletes() {
        WordList list = lists.create(alice.accountId(), "Famille");
        lists.add(alice.accountId(), list.id(), TestSigns.CHAT.uid());
        String uri = "/lists/" + list.id();

        assertThat(mvc.post().uri(uri + "/rename").with(as(alice)).with(csrf()).param("name", "Ma famille")
                .exchange()).hasRedirectedUrl(uri);
        assertThat(mvc.post().uri(uri + "/signs/3/remove").with(as(alice)).with(csrf()).exchange())
                .hasRedirectedUrl(uri);
        assertThat(lists.get(alice.accountId(), list.id())).satisfies(renamed -> {
            assertThat(renamed.name()).isEqualTo("Ma famille");
            assertThat(renamed.signUids()).isEmpty();
        });

        assertThat(mvc.post().uri(uri + "/delete").with(as(alice)).with(csrf()).exchange())
                .hasRedirectedUrl("/lists");
        assertThat(lists.lists(alice.accountId())).isEmpty();
    }

    @Test
    void listsOfOtherUsersAreNotFound() {
        WordList list = lists.create(alice.accountId(), "Famille");

        MvcTestResult result = mvc.get().uri("/lists/" + list.id()).with(as(bob)).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyText().contains("Liste introuvable");
        assertThat(mvc.post().uri("/lists/" + list.id() + "/delete").with(as(bob)).with(csrf()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(lists.lists(alice.accountId())).hasSize(1);
    }

    @Test
    void signPagesOfferToAddTheSignToLists() {
        WordList family = lists.create(alice.accountId(), "Famille");
        WordList work = lists.create(alice.accountId(), "Travail");
        lists.add(alice.accountId(), work.id(), TestSigns.LAUSANNE.uid());

        MvcTestResult result = mvc.get().uri("/signs/1").with(as(alice)).exchange();

        assertThat(result).bodyText()
                .contains("action=\"/signs/1/lists/" + family.id() + "\"")
                .contains("action=\"/signs/1/lists/" + work.id() + "/remove\"")
                .contains("action=\"/signs/1/lists\"");
    }

    @Test
    void anonymousUsersAreInvitedToLogIn() {
        assertThat(mvc.get().uri("/signs/1").exchange()).bodyText()
                .contains("Connectez-vous pour ajouter ce signe à vos listes.");
    }

    @Test
    void addsAndRemovesTheSignFromItsPage() {
        WordList list = lists.create(alice.accountId(), "Famille");
        String uri = "/signs/1/lists/" + list.id();

        assertThat(mvc.post().uri(uri).with(as(alice)).with(csrf()).exchange()).hasRedirectedUrl("/signs/1");
        assertThat(lists.get(alice.accountId(), list.id()).signUids()).containsExactly("1");

        MvcTestResult removed = mvc.post().uri(uri + "/remove").with(as(alice)).with(csrf())
                .header("HX-Request", "true").exchange();

        assertThat(removed).hasStatus(HttpStatus.OK);
        assertThat(removed).bodyText().doesNotContain("<html").contains("action=\"" + uri + "\"");
        assertThat(lists.get(alice.accountId(), list.id()).signUids()).isEmpty();
    }

    @Test
    void createsAListWithTheSign() {
        MvcTestResult result = mvc.post().uri("/signs/1/lists").with(as(alice)).with(csrf())
                .param("name", "Villes").exchange();

        assertThat(result).hasRedirectedUrl("/signs/1");
        assertThat(lists.lists(alice.accountId())).singleElement().satisfies(list -> {
            assertThat(list.name()).isEqualTo("Villes");
            assertThat(list.signUids()).isEqualTo(List.of("1"));
        });
    }

    private static RequestPostProcessor as(LocalUser user) {
        return user(user);
    }
}
