package fr.ledcom.signaire.web;

import static fr.ledcom.signaire.web.TestUsers.ALICE;
import static fr.ledcom.signaire.web.TestUsers.ALICE_ID;
import static fr.ledcom.signaire.web.TestUsers.BOB;
import static fr.ledcom.signaire.web.TestUsers.BOB_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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

import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListException;
import fr.ledcom.signaire.accounts.WordListException.Reason;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;

@WebLayerTest({ListController.class, SignController.class})
class ListPagesTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WordListService lists;

    @Autowired
    private SignIndex index;

    @BeforeEach
    void indexSigns() {
        index.recreate();
        index.index(TestSigns.ALL);
        index.refresh();
    }

    @Test
    void showsTheListsOfTheUser() {
        when(lists.lists(ALICE_ID)).thenReturn(List.of(
                new WordList(10, "Famille", List.of(TestSigns.CHAT.uid(), TestSigns.LAUSANNE.uid()))));

        MvcTestResult result = mvc.get().uri("/lists").with(ALICE).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().contains("href=\"/lists/10\">Famille</a>", "2 signes");
    }

    @Test
    void saysWhenTheUserHasNoList() {
        assertThat(mvc.get().uri("/lists").with(ALICE).exchange()).bodyText()
                .contains("Vous n&#39;avez pas encore de liste.");
    }

    @Test
    void createsLists() {
        when(lists.create(ALICE_ID, "Famille")).thenReturn(new WordList(10, "Famille", List.of()));

        MvcTestResult result = mvc.post().uri("/lists").with(ALICE).with(csrf()).param("name", "Famille").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/lists/10");
    }

    @Test
    void explainsWhyAListCannotBeCreated() {
        when(lists.create(eq(ALICE_ID), anyString()))
                .thenThrow(new WordListException(Reason.NAME_TAKEN, "taken"))
                .thenThrow(new WordListException(Reason.INVALID_NAME, "blank"));

        assertThat(mvc.post().uri("/lists").with(ALICE).with(csrf()).param("name", "famille").exchange())
                .bodyText().contains("Vous avez déjà une liste de ce nom.");
        assertThat(mvc.post().uri("/lists").with(ALICE).with(csrf()).param("name", " ").exchange())
                .bodyText().contains("Le nom doit compter 1 à 100 caractères.");
    }

    @Test
    void showsTheSignsOfAList() throws UnsupportedEncodingException {
        when(lists.get(ALICE_ID, 10)).thenReturn(
                new WordList(10, "Famille", List.of(TestSigns.LAUSANNE.uid(), TestSigns.CHAT.uid())));

        MvcTestResult result = mvc.get().uri("/lists/10").with(ALICE)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText()
                .contains("<h1>Famille</h1>", "2 Gebärden")
                .contains("href=\"/signs/1\">LAUSANNE</a>", "href=\"/signs/3\">CHAT</a>")
                .contains("action=\"/lists/10/signs/1/remove\"");
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body.indexOf("LAUSANNE")).isLessThan(body.indexOf("CHAT"));
    }

    @Test
    void mentionsSignsThatAreNoLongerInTheLexicon() {
        when(lists.get(ALICE_ID, 10)).thenReturn(new WordList(10, "Famille", List.of("999999")));

        assertThat(mvc.get().uri("/lists/10").with(ALICE).exchange()).bodyText()
                .contains("Signes absents du lexique : 1.");
    }

    @Test
    void renamesRemovesAndDeletes() {
        assertThat(mvc.post().uri("/lists/10/rename").with(ALICE).with(csrf()).param("name", "Ma famille")
                .exchange()).hasRedirectedUrl("/lists/10");
        verify(lists).rename(ALICE_ID, 10, "Ma famille");

        assertThat(mvc.post().uri("/lists/10/signs/3/remove").with(ALICE).with(csrf()).exchange())
                .hasRedirectedUrl("/lists/10");
        verify(lists).remove(ALICE_ID, 10, "3");

        assertThat(mvc.post().uri("/lists/10/delete").with(ALICE).with(csrf()).exchange())
                .hasRedirectedUrl("/lists");
        verify(lists).delete(ALICE_ID, 10);
    }

    @Test
    void explainsWhyAListCannotBeRenamed() {
        when(lists.rename(ALICE_ID, 10, "Travail")).thenThrow(new WordListException(Reason.NAME_TAKEN, "taken"));
        when(lists.get(ALICE_ID, 10)).thenReturn(new WordList(10, "Famille", List.of()));

        assertThat(mvc.post().uri("/lists/10/rename").with(ALICE).with(csrf()).param("name", "Travail").exchange())
                .bodyText().contains("<h1>Famille</h1>", "Vous avez déjà une liste de ce nom.");
    }

    @Test
    void listsOfOtherUsersAreNotFound() {
        WordListException notFound = new WordListException(Reason.NOT_FOUND, "not found");
        when(lists.get(BOB_ID, 10)).thenThrow(notFound);
        doThrow(notFound).when(lists).delete(BOB_ID, 10);

        MvcTestResult result = mvc.get().uri("/lists/10").with(BOB).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyText().contains("Liste introuvable");
        assertThat(mvc.post().uri("/lists/10/delete").with(BOB).with(csrf()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void signPagesOfferToAddTheSignToLists() {
        when(lists.lists(ALICE_ID)).thenReturn(List.of(new WordList(10, "Famille", List.of()),
                new WordList(11, "Travail", List.of(TestSigns.LAUSANNE.uid()))));
        when(lists.listsContaining(ALICE_ID, TestSigns.LAUSANNE.uid())).thenReturn(List.of(11L));

        MvcTestResult result = mvc.get().uri("/signs/1").with(ALICE).exchange();

        assertThat(result).bodyText()
                .contains("action=\"/signs/1/lists/10\"")
                .contains("action=\"/signs/1/lists/11/remove\"")
                .contains("action=\"/signs/1/lists\"");
    }

    @Test
    void anonymousUsersAreInvitedToLogIn() {
        assertThat(mvc.get().uri("/signs/1").exchange()).bodyText()
                .contains("Connectez-vous pour ajouter ce signe à vos listes.");
    }

    @Test
    void addsAndRemovesTheSignFromItsPage() {
        when(lists.lists(ALICE_ID)).thenReturn(List.of(new WordList(10, "Famille", List.of())));
        String uri = "/signs/1/lists/10";

        assertThat(mvc.post().uri(uri).with(ALICE).with(csrf()).exchange()).hasRedirectedUrl("/signs/1");
        verify(lists).add(ALICE_ID, 10, "1");

        MvcTestResult removed = mvc.post().uri(uri + "/remove").with(ALICE).with(csrf())
                .header("HX-Request", "true").exchange();

        assertThat(removed).hasStatus(HttpStatus.OK);
        assertThat(removed).bodyText().doesNotContain("<html").contains("action=\"" + uri + "\"");
        verify(lists).remove(ALICE_ID, 10, "1");
    }

    @Test
    void createsAListWithTheSign() {
        when(lists.create(ALICE_ID, "Villes")).thenReturn(new WordList(12, "Villes", List.of()));

        MvcTestResult result = mvc.post().uri("/signs/1/lists").with(ALICE).with(csrf())
                .param("name", "Villes").exchange();

        assertThat(result).hasRedirectedUrl("/signs/1");
        verify(lists).add(ALICE_ID, 12, "1");
    }
}
