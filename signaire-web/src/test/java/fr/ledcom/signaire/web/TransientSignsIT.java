package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.AccountsCleaner;
import fr.ledcom.signaire.accounts.SignRequest;
import fr.ledcom.signaire.accounts.SignRequestService;
import fr.ledcom.signaire.accounts.WordList;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/**
 * Signs are transient: the bundles and the index can be deleted and made again from SignSuisse at any time.
 * Everything that refers to signs (lists, changes of administrators, requests) keeps working, because signs
 * are identified by their SignSuisse uid, or by the uid given by Signaire to the signs it added.
 */
@WebIntegrationTest
class TransientSignsIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private AccountsCleaner cleaner;

    @Autowired
    private WordListService lists;

    @Autowired
    private SignRequestService requests;

    @Autowired
    private SignEditor editor;

    @Autowired
    private SignIndex index;

    @Autowired
    private SignSearchService search;

    @Autowired
    private StartupLoading loading;

    @Autowired
    private AdminUsers admins;

    @Value("${signaire.bundles.directory}")
    private Path bundles;

    private final BundleWriter writer = new BundleWriter(Clock.systemUTC());

    @BeforeEach
    void clean() throws IOException {
        cleaner.deleteAll();
        index.recreate();
        deleteBundles();
    }

    @Test
    void everythingSurvivesTheDeletionAndReloadOfAllTheSigns() throws IOException {
        writeBundles();
        loading.load();
        long alice = accounts.register("alice", "correct horse", "Alice").id();
        long admin = accounts.register("admin", "correct horse", "Admin").id();
        WordList list = lists.create(alice, "Famille");
        lists.addAll(alice, list.id(), List.of(TestSigns.LAUSANNE.uid(), TestSigns.CHAT.uid()));
        editor.save(TestSigns.GENEVE.toBuilder().definition("Ville du bout du lac").build(), admin);
        Sign added = Sign.builder("local-0001", Language.FRENCH, "SIGNAIRE").definition("Ce logiciel").build();
        editor.save(added, admin);
        SignRequest request = requests.request(alice, "Signaire", Language.FRENCH, null);
        requests.markAdded(request.id(), added.uid());

        // all the signs are gone
        deleteBundles();
        index.recreate();
        loading.load();
        assertThat(search.findByUid(TestSigns.LAUSANNE.uid())).isEmpty();
        assertThat(mvc.get().uri("/lists/" + list.id()).with(user(localUser("alice"))).exchange()).bodyText()
                .contains("Signes absents du lexique : 2.");

        // and made again from scratch
        writeBundles();
        loading.load();

        assertThat(mvc.get().uri("/lists/" + list.id()).with(user(localUser("alice"))).exchange()).bodyText()
                .contains("LAUSANNE", "CHAT").doesNotContain("absents");
        assertThat(search.findByUid(TestSigns.GENEVE.uid()).orElseThrow().definition())
                .isEqualTo("Ville du bout du lac");
        assertThat(search.findByUid(added.uid())).contains(added);
        assertThat(mvc.get().uri("/signs/" + requests.get(request.id()).orElseThrow().signUid()).exchange())
                .bodyText().contains("SIGNAIRE");
    }

    private LocalUser localUser(String username) {
        return new LocalUser(accounts.findLocalCredentials(username).orElseThrow(), admins);
    }

    private void writeBundles() {
        writer.write(bundles.resolve("city.zip"), Category.CITY,
                List.of(TestSigns.LAUSANNE.toBuilder().videos(List.of()).build(), TestSigns.GENEVE),
                bundles.resolve(".media"));
        writer.write(bundles.resolve("animals.zip"), Category.ANIMALS, List.of(TestSigns.CHAT, TestSigns.KATZE),
                bundles.resolve(".media"));
    }

    private void deleteBundles() throws IOException {
        Files.createDirectories(bundles);
        try (Stream<Path> files = Files.list(bundles)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".zip")).toList()) {
                Files.delete(file);
            }
        }
    }
}
