package fr.ledcom.signaire.web;

import static fr.ledcom.signaire.web.TestUsers.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
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
    void showsTheSearchesWhileFindingTheSigns() {
        when(sync.progress()).thenReturn(new Progress(State.RUNNING, Category.CITY, Language.FRENCH, true, 40, 75,
                12, 0, 0, 0, 0, List.of(), null));

        assertThat(mvc.get().uri("/admin/sync/status").with(ADMIN).exchange()).bodyText()
                .contains("Ville (fr) : recherche des signes dans SignSuisse, 40 recherches faites, 75 prévues pour "
                        + "l&#39;instant, 12 signes trouvés.", "every 2s");
        assertThat(mvc.get().uri("/admin/sync/status").with(ADMIN).header(HttpHeaders.ACCEPT_LANGUAGE, "de")
                .exchange()).bodyText().contains("40 Suchen gemacht, 75 bisher geplant, 12 Gebärden gefunden");
    }

    @Test
    void showsTheSignsFoundOnceTheyAreAllFound() {
        when(sync.progress()).thenReturn(new Progress(State.RUNNING, Category.CITY, Language.FRENCH, false, 40, 0, 12,
                3, 1, 0, 0, List.of(), null));

        assertThat(mvc.get().uri("/admin/sync/status").with(ADMIN).exchange()).bodyText()
                .contains("Ville (fr) : 12 signes trouvés", "Signes extraits : 3, en échec : 1.")
                .doesNotContain("recherches", "Médias");
    }

    @Test
    void showsTheMediaDownloaded() {
        when(sync.progress()).thenReturn(new Progress(State.RUNNING, Category.CITY, Language.FRENCH, false, 40, 0, 12,
                3, 0, 9, 7, List.of(), null));

        assertThat(mvc.get().uri("/admin/sync/status").with(ADMIN).exchange()).bodyText()
                .contains("Médias téléchargés : 7 sur 9.");
    }
}
