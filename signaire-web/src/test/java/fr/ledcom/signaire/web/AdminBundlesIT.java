package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.AccountsCleaner;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;

@WebIntegrationTest
class AdminBundlesIT {

    @TempDir
    Path elsewhere;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private AccountsCleaner cleaner;

    @Autowired
    private SignIndex index;

    @Autowired
    private SignSearchService search;

    @Autowired
    private AdminUsers admins;

    @Autowired
    private SignSuisseSync sync;

    @Value("${signaire.bundles.directory}")
    private Path bundles;

    private final BundleWriter writer = new BundleWriter(Clock.systemUTC());
    private LocalUser admin;

    @BeforeEach
    void setUp() throws IOException {
        cleaner.deleteAll();
        index.recreate();
        deleteBundles();
        accounts.register("admin", "correct horse", "Admin");
        admin = new LocalUser(accounts.findLocalCredentials("admin").orElseThrow(), admins);
    }

    @AfterEach
    void deleteBundles() throws IOException {
        Files.createDirectories(bundles);
        try (Stream<Path> files = Files.list(bundles)) {
            for (Path file : files.filter(f -> !Files.isDirectory(f)).toList()) {
                Files.delete(file);
            }
        }
    }

    @Test
    void listsTheBundlesAndReloadsThem() {
        writer.write(bundles.resolve("city.zip"), Category.CITY, List.of(TestSigns.LAUSANNE, TestSigns.GENEVE),
                bundles.resolve(".media"));

        MvcTestResult reloaded = mvc.post().uri("/admin/bundles/reload").with(user(admin)).with(csrf()).exchange();

        assertThat(reloaded).hasRedirectedUrl("/admin/bundles");
        assertThat(reloaded).flash().containsEntry("status", "Bundles chargés : 1, inchangés : 0, en échec : 0.");
        assertThat(search.findByUid(TestSigns.LAUSANNE.uid())).isPresent();
        assertThat(mvc.get().uri("/admin/bundles").with(user(admin)).exchange()).bodyText()
                .contains("city.zip", "Ville", ">2<");
    }

    @Test
    void reindexesEverything() {
        writer.write(bundles.resolve("city.zip"), Category.CITY, List.of(TestSigns.LAUSANNE),
                bundles.resolve(".media"));
        index.index(List.of(TestSigns.CHAT));
        index.refresh();

        mvc.post().uri("/admin/bundles/reindex").with(user(admin)).with(csrf()).exchange();

        assertThat(search.findByUid(TestSigns.LAUSANNE.uid())).isPresent();
        assertThat(search.findByUid(TestSigns.CHAT.uid())).isEmpty();
    }

    @Test
    void uploadsBundles() throws IOException {
        Path bundle = writer.write(elsewhere.resolve("whatever-name.zip"), Category.ANIMALS,
                List.of(TestSigns.CHAT, TestSigns.KATZE), elsewhere);

        MvcTestResult result = mvc.perform(multipart("/admin/bundles")
                .file(new MockMultipartFile("bundle", "whatever-name.zip", "application/zip",
                        Files.readAllBytes(bundle)))
                .with(user(admin)).with(csrf()));

        assertThat(result).hasRedirectedUrl("/admin/bundles");
        assertThat(result).flash().containsEntry("status", "animals.zip : 2 signes chargés.");
        assertThat(bundles.resolve("animals.zip")).exists();
        assertThat(search.findByUid(TestSigns.KATZE.uid())).isPresent();
    }

    @Test
    void rejectsInvalidBundles() throws IOException {
        MvcTestResult result = mvc.perform(multipart("/admin/bundles")
                .file(new MockMultipartFile("bundle", "broken.zip", "application/zip",
                        "not a zip".getBytes(StandardCharsets.UTF_8)))
                .with(user(admin)).with(csrf()));

        assertThat(result).hasRedirectedUrl("/admin/bundles");
        assertThat(result).flash().hasEntrySatisfying("status",
                status -> assertThat(status).asString().startsWith("Bundle invalide"));
        try (Stream<Path> files = Files.list(bundles)) {
            assertThat(files.filter(f -> !Files.isDirectory(f))).isEmpty();
        }
    }

    @Test
    void synchronizesWithSignSuisse() throws Exception {
        MvcTestResult started = mvc.post().uri("/admin/sync").with(user(admin)).with(csrf())
                .param("categories", "CITY").param("languages", "fr").exchange();

        assertThat(started).hasRedirectedUrl("/admin/sync");
        assertThat(sync.awaitCompletion(Duration.ofSeconds(30))).isTrue();
        assertThat(bundles.resolve("city.zip")).exists();
        assertThat(search.findByUid(FakeSignSuisse.ZOUG.uid())).isPresent();
        assertThat(mvc.get().uri("/admin/sync/status").with(user(admin)).exchange()).bodyText()
                .contains("Synchronisation terminée", "Ville", "2 signes");
    }

    @Test
    void theSynchronizationPageOffersAllCategories() {
        assertThat(mvc.get().uri("/admin/sync").with(user(admin)).exchange()).bodyText()
                .contains("name=\"categories\"", "value=\"EVENTS_MEDIA\"", "name=\"languages\"", "value=\"it\"",
                        "name=\"withMedia\"");
    }
}
