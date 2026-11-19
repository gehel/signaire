package fr.ledcom.signaire.web;

import static fr.ledcom.signaire.web.TestUsers.ADMIN;
import static fr.ledcom.signaire.web.TestUsers.ADMIN_ID;
import static fr.ledcom.signaire.web.TestUsers.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.SignOverride;
import fr.ledcom.signaire.accounts.SignOverrideService;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/** Signs changed by administrators: stored as overrides (mocks here), applied to the index. */
@WebLayerTest({AdminController.class, AdminSignsController.class, SearchController.class, SignController.class})
@Import({SignEditor.class, StartupLoading.class})
class AdminSignsTest {

    @Autowired
    private MockMvcTester mvc;

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

    @BeforeEach
    void setUp() throws IOException {
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
    }

    @Test
    void administrationIsForAdministratorsOnly() {
        assertThat(mvc.get().uri("/admin").with(ALICE).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/admin").exchange()).hasStatus(HttpStatus.FOUND);
        assertThat(mvc.get().uri("/admin").with(ADMIN).exchange()).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/").with(ADMIN).exchange()).bodyText().contains("href=\"/admin\"");
        assertThat(mvc.get().uri("/").with(ALICE).exchange()).bodyText().doesNotContain("href=\"/admin\"");
    }

    @Test
    void createsSigns() {
        when(overrides.newUid()).thenReturn("local-1");

        MvcTestResult result = mvc.post().uri("/admin/signs").with(ADMIN).with(csrf())
                .param("name", "ZÜRICHSEE").param("language", "de").param("categories", "NATURE", "CITY")
                .param("definition", "See bei Zürich").param("dialects", "ZH, SZ")
                .param("videos", "https://example.org/zurichsee.mp4\n")
                .param("examples", "Wir baden im Zürichsee.\n\nDer See ist kalt.")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/signs/local-1");
        Sign created = search.findByUid("local-1").orElseThrow();
        assertThat(created.name()).isEqualTo("ZÜRICHSEE");
        assertThat(created.language()).isEqualTo(Language.GERMAN);
        assertThat(created.categories()).containsExactly(Category.NATURE, Category.CITY);
        assertThat(created.dialects()).containsExactly("ZH", "SZ");
        assertThat(created.videos()).singleElement().satisfies(video -> {
            assertThat(video.sourceUrl()).isEqualTo("https://example.org/zurichsee.mp4");
            assertThat(video.file()).isNull();
        });
        assertThat(created.examples()).hasSize(2);
        verify(overrides).save(created, ADMIN_ID);
    }

    @Test
    void editsSignsKeepingTheirDownloadedMedia() {
        assertThat(mvc.get().uri("/admin/signs/1/edit").with(ADMIN).exchange()).bodyText()
                .contains("value=\"LAUSANNE\"", "Capitale du canton de Vaud", "https://example.org/lausanne.mp4");

        MvcTestResult result = mvc.post().uri("/admin/signs/1").with(ADMIN).with(csrf())
                .param("name", "LAUSANNE").param("language", "fr").param("categories", "CITY")
                .param("definition", "Ville olympique").param("videos", "https://example.org/lausanne.mp4")
                .exchange();

        assertThat(result).hasRedirectedUrl("/signs/1");
        Sign edited = search.findByUid("1").orElseThrow();
        assertThat(edited.definition()).isEqualTo("Ville olympique");
        assertThat(edited.videos()).singleElement()
                .satisfies(video -> assertThat(video.file()).isEqualTo("media/lausanne.mp4"));
        verify(overrides).save(edited, ADMIN_ID);
    }

    @Test
    void deletesSigns() {
        assertThat(mvc.post().uri("/admin/signs/3/delete").with(ADMIN).with(csrf()).exchange())
                .hasRedirectedUrl("/admin/signs");

        assertThat(search.findByUid("3")).isEmpty();
        verify(overrides).delete("3", ADMIN_ID);
    }

    @Test
    void listsTheChanges() {
        when(overrides.all()).thenReturn(List.of(new SignOverride("3", null, true, Instant.now(), "admin")));

        assertThat(mvc.get().uri("/admin/signs").with(ADMIN).exchange()).bodyText()
                .contains("Supprimé", "3", "admin");
    }

    @Test
    void changesAreAppliedAgainAfterLoadingTheBundles() {
        mvc.post().uri("/admin/signs/1").with(ADMIN).with(csrf())
                .param("name", "LAUSANNE").param("language", "fr").param("definition", "Ville olympique").exchange();
        ArgumentCaptor<Sign> saved = ArgumentCaptor.forClass(Sign.class);
        verify(overrides).save(saved.capture(), eq(ADMIN_ID));
        when(overrides.all()).thenReturn(List.of(
                new SignOverride("1", saved.getValue(), false, Instant.now(), "admin"),
                new SignOverride("2", null, true, Instant.now(), "admin")));

        index.recreate();
        loading.load();

        assertThat(search.findByUid("1").orElseThrow().definition()).isEqualTo("Ville olympique");
        assertThat(search.findByUid("2")).isEmpty();
    }

    @Test
    void revertsChangesToTheSignOfTheBundles() {
        mvc.post().uri("/admin/signs/1").with(ADMIN).with(csrf())
                .param("name", "LAUSANNE").param("language", "fr").param("definition", "Ville olympique").exchange();

        assertThat(mvc.post().uri("/admin/signs/1/revert").with(ADMIN).with(csrf()).exchange())
                .hasRedirectedUrl("/admin/signs");

        assertThat(search.findByUid("1").orElseThrow().definition()).isEqualTo("Capitale du canton de Vaud");
        verify(overrides).revert("1");
    }

    @Test
    void explainsWhatIsWrongWithASign() {
        MvcTestResult result = mvc.post().uri("/admin/signs").with(ADMIN).with(csrf())
                .param("name", " ").param("language", "fr").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().contains("Le nom du signe est obligatoire.");
    }

    @Test
    void signPagesLinkToTheirEditionForAdministrators() {
        assertThat(mvc.get().uri("/signs/1").with(ADMIN).exchange()).bodyText()
                .contains("href=\"/admin/signs/1/edit\"", "action=\"/admin/signs/1/delete\"");
        assertThat(mvc.get().uri("/signs/1").with(ALICE).exchange()).bodyText()
                .doesNotContain("/admin/signs/1/edit");
    }
}
