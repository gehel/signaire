package fr.ledcom.signaire.web;

import static fr.ledcom.signaire.web.TestUsers.ALICE;
import static fr.ledcom.signaire.web.TestUsers.ALICE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListException;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;

@WebLayerTest({SearchController.class, SearchToListController.class})
class SearchToListTest {

    private static final WordList FAMILY = new WordList(10, "Famille", List.of());

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WordListService lists;

    @Autowired
    private SignIndex index;

    @BeforeEach
    void setUp() {
        index.recreate();
        index.index(TestSigns.ALL);
        index.refresh();
        when(lists.lists(ALICE_ID)).thenReturn(List.of(FAMILY));
        when(lists.get(ALICE_ID, FAMILY.id())).thenReturn(FAMILY);
    }

    @Test
    void resultsCanBeSelectedByLoggedInUsers() {
        MvcTestResult result = mvc.get().uri("/?category=CITY").with(ALICE).exchange();

        assertThat(result).bodyText()
                .contains("form=\"add-to-list\"", "name=\"uid\"", "value=\"1\"")
                .contains("<option value=\"10\">Famille</option>")
                .contains("Ajouter la sélection", "Ajouter tous les résultats (2)");
    }

    @Test
    void anonymousUsersCannotSelectResults() {
        assertThat(mvc.get().uri("/?category=CITY").exchange()).bodyText().doesNotContain("add-to-list");
    }

    @Test
    void addsTheSelectedSigns() {
        when(lists.addAll(ALICE_ID, FAMILY.id(), List.of("3", "1"))).thenReturn(2);

        MvcTestResult result = mvc.post().uri("/lists/signs").with(ALICE).with(csrf())
                .param("scope", "selection").param("uid", "3", "1").param("list", "10")
                .param("q", "").param("category", "CITY", "ANIMALS")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND)
                .hasRedirectedUrl("/?category=CITY&category=ANIMALS")
                .flash().containsEntry("status", "2 signes ajoutés à « Famille ».");
        verify(lists).addAll(ALICE_ID, FAMILY.id(), List.of("3", "1"));
    }

    @Test
    void addsAllTheResultsOfTheSearch() {
        mvc.post().uri("/lists/signs").with(ALICE).with(csrf())
                .param("scope", "all").param("uid", "3").param("list", "10")
                .param("category", "CITY")
                .exchange();

        verify(lists).addAll(ALICE_ID, FAMILY.id(), List.of("2", "1"));
    }

    @Test
    void addsToANewList() {
        when(lists.create(ALICE_ID, "Villes")).thenReturn(new WordList(11, "Villes", List.of()));
        when(lists.addAll(ALICE_ID, 11, List.of("1"))).thenReturn(1);

        MvcTestResult result = mvc.post().uri("/lists/signs").with(ALICE).with(csrf())
                .header("HX-Request", "true")
                .param("scope", "all").param("list", "").param("newList", "Villes").param("q", "lausanne")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK)
                .bodyText().doesNotContain("<html").contains("1 signe ajouté à « Villes ».");
    }

    @Test
    void explainsWhatIsMissing() {
        when(lists.create(ALICE_ID, " ")).thenThrow(new WordListException(WordListException.Reason.INVALID_NAME,
                "blank"));

        assertThat(mvc.post().uri("/lists/signs").with(ALICE).with(csrf()).header("HX-Request", "true")
                .param("scope", "selection").param("list", "10").exchange())
                .bodyText().contains("Cochez d&#39;abord des signes.");
        assertThat(mvc.post().uri("/lists/signs").with(ALICE).with(csrf()).header("HX-Request", "true")
                .param("scope", "all").param("list", "").param("newList", " ").exchange())
                .bodyText().contains("Le nom doit compter 1 à 100 caractères.");
        verify(lists, never()).addAll(anyLong(), anyLong(), anyList());
    }

    @Test
    void requiresLogin() {
        assertThat(mvc.post().uri("/lists/signs").with(csrf()).param("scope", "all").exchange())
                .hasStatus(HttpStatus.FOUND);
        verify(lists, never()).create(anyLong(), anyString());
    }
}
