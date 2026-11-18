package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.AccountsCleaner;
import fr.ledcom.signaire.accounts.SignOverrideService;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

@WebIntegrationTest
class AdminSignsIT {

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
    private SignOverrideService overrides;

    @Autowired
    private StartupLoading loading;

    @Value("${signaire.bundles.directory}")
    private Path bundles;

    @Autowired
    private AdminUsers admins;

    private LocalUser admin;
    private LocalUser alice;

    @BeforeEach
    void setUp() throws IOException {
        cleaner.deleteAll();
        index.recreate();
        Files.createDirectories(bundles);
        try (var files = Files.list(bundles)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".zip")).toList()) {
                Files.delete(file);
            }
        }
        new BundleWriter(Clock.systemUTC()).write(bundles.resolve("city.zip"), Category.CITY,
                List.of(TestSigns.LAUSANNE.toBuilder().videos(List.of()).build(), TestSigns.GENEVE),
                bundles.resolve(".media"));
        loading.load();
        index.index(TestSigns.ALL);
        index.refresh();
        accounts.register("admin", "correct horse", "Admin");
        accounts.register("alice", "correct horse", "Alice");
        admin = new LocalUser(accounts.findLocalCredentials("admin").orElseThrow(), admins);
        alice = new LocalUser(accounts.findLocalCredentials("alice").orElseThrow(), admins);
    }

    @Test
    void administrationIsForAdministratorsOnly() {
        assertThat(admin.getAuthorities()).extracting(Object::toString).contains("ROLE_ADMIN");
        assertThat(mvc.get().uri("/admin").with(user(alice)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/admin").exchange()).hasStatus(HttpStatus.FOUND);
        assertThat(mvc.get().uri("/admin").with(user(admin)).exchange()).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/").with(user(admin)).exchange()).bodyText().contains("href=\"/admin\"");
        assertThat(mvc.get().uri("/").with(user(alice)).exchange()).bodyText().doesNotContain("href=\"/admin\"");
    }

    @Test
    void createsSigns() {
        MvcTestResult result = mvc.post().uri("/admin/signs").with(user(admin)).with(csrf())
                .param("name", "ZÜRICHSEE").param("language", "de").param("categories", "NATURE", "CITY")
                .param("definition", "See bei Zürich").param("dialects", "ZH, SZ")
                .param("videos", "https://example.org/zurichsee.mp4\n")
                .param("examples", "Wir baden im Zürichsee.\n\nDer See ist kalt.")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        String uid = result.getResponse().getRedirectedUrl().substring("/signs/".length());
        assertThat(uid).startsWith("local-");
        Sign created = search.findByUid(uid).orElseThrow();
        assertThat(created.name()).isEqualTo("ZÜRICHSEE");
        assertThat(created.language()).isEqualTo(Language.GERMAN);
        assertThat(created.categories()).containsExactly(Category.NATURE, Category.CITY);
        assertThat(created.dialects()).containsExactly("ZH", "SZ");
        assertThat(created.videos()).singleElement().satisfies(video -> {
            assertThat(video.sourceUrl()).isEqualTo("https://example.org/zurichsee.mp4");
            assertThat(video.file()).isNull();
        });
        assertThat(created.examples()).hasSize(2);
        assertThat(overrides.find(uid)).isPresent();
    }

    @Test
    void editsSignsKeepingTheirDownloadedMedia() {
        assertThat(mvc.get().uri("/admin/signs/1/edit").with(user(admin)).exchange()).bodyText()
                .contains("value=\"LAUSANNE\"", "Capitale du canton de Vaud", "https://example.org/lausanne.mp4");

        MvcTestResult result = mvc.post().uri("/admin/signs/1").with(user(admin)).with(csrf())
                .param("name", "LAUSANNE").param("language", "fr").param("categories", "CITY")
                .param("definition", "Ville olympique").param("videos", "https://example.org/lausanne.mp4")
                .exchange();

        assertThat(result).hasRedirectedUrl("/signs/1");
        Sign edited = search.findByUid("1").orElseThrow();
        assertThat(edited.definition()).isEqualTo("Ville olympique");
        assertThat(edited.videos()).singleElement()
                .satisfies(video -> assertThat(video.file()).isEqualTo("media/lausanne.mp4"));
    }

    @Test
    void deletesSigns() {
        assertThat(mvc.post().uri("/admin/signs/3/delete").with(user(admin)).with(csrf()).exchange())
                .hasRedirectedUrl("/admin/signs");

        assertThat(search.findByUid("3")).isEmpty();
        assertThat(mvc.get().uri("/admin/signs").with(user(admin)).exchange()).bodyText()
                .contains("Supprimé", "3", "admin");
    }

    @Test
    void changesSurviveTheReloadOfTheBundles() {
        mvc.post().uri("/admin/signs/1").with(user(admin)).with(csrf())
                .param("name", "LAUSANNE").param("language", "fr").param("definition", "Ville olympique").exchange();
        mvc.post().uri("/admin/signs/2/delete").with(user(admin)).with(csrf()).exchange();

        index.recreate();
        loading.load();

        assertThat(search.findByUid("1").orElseThrow().definition()).isEqualTo("Ville olympique");
        assertThat(search.findByUid("2")).isEmpty();
    }

    @Test
    void revertsChangesToTheSignOfTheBundles() {
        mvc.post().uri("/admin/signs/1").with(user(admin)).with(csrf())
                .param("name", "LAUSANNE").param("language", "fr").param("definition", "Ville olympique").exchange();

        assertThat(mvc.post().uri("/admin/signs/1/revert").with(user(admin)).with(csrf()).exchange())
                .hasRedirectedUrl("/admin/signs");

        assertThat(search.findByUid("1").orElseThrow().definition()).isEqualTo("Capitale du canton de Vaud");
        assertThat(overrides.find("1")).isEmpty();
    }

    @Test
    void explainsWhatIsWrongWithASign() {
        MvcTestResult result = mvc.post().uri("/admin/signs").with(user(admin)).with(csrf())
                .param("name", " ").param("language", "fr").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().contains("Le nom du signe est obligatoire.");
    }

    @Test
    void signPagesLinkToTheirEditionForAdministrators() {
        assertThat(mvc.get().uri("/signs/1").with(user(admin)).exchange()).bodyText()
                .contains("href=\"/admin/signs/1/edit\"", "action=\"/admin/signs/1/delete\"");
        assertThat(mvc.get().uri("/signs/1").with(user(alice)).exchange()).bodyText()
                .doesNotContain("/admin/signs/1/edit");
    }
}
