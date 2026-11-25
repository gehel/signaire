package fr.ledcom.signaire.web;

import static fr.ledcom.signaire.web.TestUsers.ALICE;
import static fr.ledcom.signaire.web.TestUsers.ALICE_ID;
import static fr.ledcom.signaire.web.TestUsers.BOB;
import static fr.ledcom.signaire.web.TestUsers.BOB_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListException;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.model.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;

@WebLayerTest({ExportController.class, ListController.class, SearchController.class})
@Import(WebExports.class)
class ExportTest {

    private static final WordList FAMILY = new WordList(10, "Famille à Lausanne", List.of("1", "3"));

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WordListService lists;

    @Autowired
    private SignIndex index;

    @BeforeEach
    void setUp() {
        index.recreate();
        index.index(TestSigns.FIVE);
        index.refresh();
        when(lists.get(ALICE_ID, FAMILY.id())).thenReturn(FAMILY);
        when(lists.get(BOB_ID, FAMILY.id())).thenThrow(
                new WordListException(WordListException.Reason.NOT_FOUND, "not found"));
    }

    @Test
    void exportsListsAsPdf() {
        MvcTestResult result = mvc.get().uri("/lists/" + FAMILY.id() + "/export?format=PDF&paper=A5&perPage=2"
                + "&layout=BOOKLET").with(ALICE).exchange();

        assertThat(result).hasStatus(HttpStatus.OK).hasContentType("application/pdf");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;").contains("Famille%20%C3%A0%20Lausanne.pdf");
        assertThat(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    @Test
    void exportsListsAsAnkiDecksAndBooks() throws IOException {
        MvcTestResult anki = mvc.get().uri("/lists/" + FAMILY.id() + "/export?format=ANKI").with(ALICE)
                .exchange();
        assertThat(anki).hasStatus(HttpStatus.OK);
        assertThat(anki.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains(".apkg");
        assertThat(entries(anki)).contains("collection.anki2", "media");

        MvcTestResult epub = mvc.get().uri("/lists/" + FAMILY.id() + "/export?format=EPUB&videos=true")
                .with(ALICE).exchange();
        assertThat(epub).hasStatus(HttpStatus.OK).hasContentType("application/epub+zip");
        assertThat(entries(epub)).startsWith("mimetype").contains("OEBPS/sign-2.xhtml");
    }

    @Test
    void listsOfOtherUsersCannotBeExported() {
        assertThat(mvc.get().uri("/lists/" + FAMILY.id() + "/export?format=PDF").with(BOB).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void exportsSearchResults() {
        MvcTestResult result = mvc.get().uri("/export?format=PDF&category=CITY&perPage=1").exchange();

        assertThat(result).hasStatus(HttpStatus.OK).hasContentType("application/pdf");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("Signaire.pdf");
    }

    @Test
    void rejectsImpossibleLayouts() {
        assertThat(mvc.get().uri("/export?format=PDF&perPage=5").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listAndSearchPagesOfferExports() {
        assertThat(mvc.get().uri("/lists/" + FAMILY.id()).with(ALICE).exchange()).bodyText()
                .contains("action=\"/lists/" + FAMILY.id() + "/export\"", "Télécharger", "Livret à plier");
        assertThat(mvc.get().uri("/?q=chat").exchange()).bodyText()
                .contains("action=\"/export\"", "name=\"q\" value=\"chat\"", "Paquet Anki");
    }

    private static List<String> entries(MvcTestResult result) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(
                result.getResponse().getContentAsByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
