package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
import org.springframework.http.HttpHeaders;
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
class ExportIT {

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
    private WordList family;

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
        family = lists.create(alice.accountId(), "Famille à Lausanne");
        lists.addAll(alice.accountId(), family.id(), List.of("1", "3"));
    }

    @Test
    void exportsListsAsPdf() {
        MvcTestResult result = mvc.get().uri("/lists/" + family.id() + "/export?format=PDF&paper=A5&perPage=2"
                + "&layout=BOOKLET").with(user(alice)).exchange();

        assertThat(result).hasStatus(HttpStatus.OK).hasContentType("application/pdf");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;").contains("Famille%20%C3%A0%20Lausanne.pdf");
        assertThat(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1))
                .startsWith("%PDF");
    }

    @Test
    void exportsListsAsAnkiDecksAndBooks() throws IOException {
        MvcTestResult anki = mvc.get().uri("/lists/" + family.id() + "/export?format=ANKI").with(user(alice))
                .exchange();
        assertThat(anki).hasStatus(HttpStatus.OK);
        assertThat(anki.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains(".apkg");
        assertThat(entries(anki)).contains("collection.anki2", "media");

        MvcTestResult epub = mvc.get().uri("/lists/" + family.id() + "/export?format=EPUB&videos=true")
                .with(user(alice)).exchange();
        assertThat(epub).hasStatus(HttpStatus.OK).hasContentType("application/epub+zip");
        assertThat(entries(epub)).startsWith("mimetype").contains("OEBPS/sign-2.xhtml");
    }

    @Test
    void listsOfOtherUsersCannotBeExported() {
        assertThat(mvc.get().uri("/lists/" + family.id() + "/export?format=PDF").with(user(bob)).exchange())
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
        assertThat(mvc.get().uri("/lists/" + family.id()).with(user(alice)).exchange()).bodyText()
                .contains("action=\"/lists/" + family.id() + "/export\"", "Télécharger", "Livret à plier");
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
