package fr.ledcom.signaire.web;

import static fr.ledcom.signaire.web.TestUsers.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import fr.ledcom.signaire.extractor.ExtractionStatus;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.web.SignSuisseSync.CategoryResult;
import fr.ledcom.signaire.web.SignSuisseSync.Progress;
import fr.ledcom.signaire.web.SignSuisseSync.State;

/** The progress of the synchronization, refreshed while it runs. */
@WebLayerTest(AdminSyncController.class)
class AdminSyncTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SignSuisseSync sync;

    @Test
    void showsTheUserAgentAndAsksForAContact() {
        when(sync.progress()).thenReturn(SignSuisseSync.Progress.IDLE);

        assertThat(mvc.get().uri("/admin/sync").with(ADMIN).exchange()).bodyText()
                .contains("<code>Signaire (+https://github.com/gehel/signaire)</code>", "signaire.signsuisse.contact",
                        "href=\"/help/admin\"");
    }

    @Test
    void showsWhatIsDoneAndLeftWhileSearching() {
        ExtractionStatus status = new ExtractionStatus(Category.CITY, ExtractionStatus.Stage.SEARCHING,
                Language.FRENCH, new ExtractionStatus.Searches(40, 12, 1, true, "sch"),
                new ExtractionStatus.Signs(120, 80, 30, 2, "LAUSANNE"),
                new ExtractionStatus.Media(60, 40, 1, 12_500_000, "lausanne.mp4"), 160, 50, Duration.ofMinutes(5),
                Duration.ofMinutes(3));
        when(sync.progress()).thenReturn(new Progress(State.RUNNING, status, 95, List.of(),
                List.of(Category.NATURE), null));

        assertThat(mvc.get().uri("/admin/sync/status").with(ADMIN).exchange()).bodyText()
                .contains("Ville : recherche des signes en LSF",
                        "Recherches : 40 faites, 12 restantes connues, 1 en échec.",
                        "Signes : 120 trouvés, 80 lus sur SignSuisse, 30 repris du bundle existant, 2 en échec, "
                                + "8 en attente.",
                        "Médias à télécharger : 40 sur 60 (12 Mo), 1 en échec.",
                        "Requêtes à SignSuisse : 160 faites, 50 restantes connues.",
                        "recherche « sch »", "signe LAUSANNE", "média lausanne.mp4",
                        "Temps restant estimé : au moins 3 minutes",
                        "Signes déjà consultables : 95", "Catégories restantes : Nature", "every 2s");
        assertThat(mvc.get().uri("/admin/sync/status").with(ADMIN).header(HttpHeaders.ACCEPT_LANGUAGE, "de")
                .exchange()).bodyText().contains("Suchen: 40 gemacht, 12 bekannt ausstehend");
    }

    @Test
    void showsTheCategoriesDone() {
        when(sync.progress()).thenReturn(new Progress(State.DONE, null, 0,
                List.of(new CategoryResult(Category.CITY, 12, 0, true, 48)), List.of(), null));

        assertThat(mvc.get().uri("/admin/sync/status").with(ADMIN).exchange()).bodyText()
                .contains("Synchronisation terminée.", "Ville : 12 signes, 48 requêtes")
                .doesNotContain("every 2s", "Recherches");
    }
}
