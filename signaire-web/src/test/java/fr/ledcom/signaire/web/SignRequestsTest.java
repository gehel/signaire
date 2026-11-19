package fr.ledcom.signaire.web;

import static fr.ledcom.signaire.web.TestUsers.ADMIN;
import static fr.ledcom.signaire.web.TestUsers.ADMIN_ID;
import static fr.ledcom.signaire.web.TestUsers.ALICE;
import static fr.ledcom.signaire.web.TestUsers.ALICE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.SignOverrideService;
import fr.ledcom.signaire.accounts.SignRequest;
import fr.ledcom.signaire.accounts.SignRequest.Status;
import fr.ledcom.signaire.accounts.SignRequestService;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/** Users request missing signs; administrators import them from (a fake) SignSuisse, create them, or reject them. */
@WebLayerTest({SearchController.class, RequestController.class, AdminController.class,
    AdminRequestsController.class, AdminSignsController.class})
@Import({SignEditor.class, FakeSignSuisse.class})
class SignRequestsTest {

    private static final long REQUEST_ID = 7;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private SignIndex index;

    @Autowired
    private SignSearchService search;

    @Autowired
    private SignRequestService requests;

    @Autowired
    private SignOverrideService overrides;

    @BeforeEach
    void setUp() {
        index.recreate();
        index.index(TestSigns.ALL);
        index.refresh();
    }

    @Test
    void usersRequestMissingSigns() {
        when(requests.request(ALICE_ID, "Zoug", Language.FRENCH, "Le canton"))
                .thenReturn(request("Zoug", Language.FRENCH, "Le canton", Status.OPEN, null));
        when(requests.byRequester(ALICE_ID))
                .thenReturn(List.of(request("Zoug", Language.FRENCH, "Le canton", Status.OPEN, null)));

        assertThat(mvc.get().uri("/?q=zoug").with(ALICE).exchange()).bodyText()
                .contains("href=\"/requests?word=zoug\"");
        assertThat(mvc.get().uri("/requests?word=zoug").with(ALICE).exchange()).bodyText()
                .contains("value=\"zoug\"");

        MvcTestResult sent = mvc.post().uri("/requests").with(ALICE).with(csrf())
                .param("word", "Zoug").param("language", "fr").param("comment", "Le canton").exchange();

        assertThat(sent).hasRedirectedUrl("/requests");
        assertThat(sent).flash().containsEntry("status", "Demande envoyée : « Zoug ».");
        assertThat(mvc.get().uri("/requests").with(ALICE).exchange()).bodyText()
                .contains("Zoug", "Le canton", "En attente");
    }

    @Test
    void requestsNeedAWord() {
        when(requests.request(ALICE_ID, " ", Language.FRENCH, null))
                .thenThrow(new IllegalArgumentException("blank"));

        assertThat(mvc.post().uri("/requests").with(ALICE).with(csrf())
                .param("word", " ").param("language", "fr").exchange()).bodyText()
                .contains("Le mot est obligatoire");
    }

    @Test
    void requestsNeedAnAccount() {
        assertThat(mvc.get().uri("/requests").exchange()).hasStatus(HttpStatus.FOUND);
    }

    @Test
    void usersSeeWhatBecameOfTheirRequests() {
        when(requests.byRequester(ALICE_ID)).thenReturn(List.of(
                request("Zoug", Language.FRENCH, null, Status.ADDED, "900"),
                request("Xyz", Language.FRENCH, null, Status.REJECTED, null)));

        assertThat(mvc.get().uri("/requests").with(ALICE).exchange()).bodyText()
                .contains("Ajouté", "href=\"/signs/900\"", "Refusé");
    }

    @Test
    void administratorsSeeTheOpenRequests() {
        SignRequest zoug = request("Zoug", Language.FRENCH, "Le canton", Status.OPEN, null);
        when(requests.openCount()).thenReturn(1L);
        when(requests.open()).thenReturn(List.of(zoug));

        assertThat(mvc.get().uri("/admin").with(ADMIN).exchange()).bodyText()
                .contains("href=\"/admin/requests\"", "1 demande en attente");
        assertThat(mvc.get().uri("/admin/requests").with(ADMIN).exchange()).bodyText()
                .contains("Zoug", "alice", "Le canton");
    }

    @Test
    void administratorsImportRequestedSignsFromSignSuisse() {
        when(requests.get(REQUEST_ID)).thenReturn(Optional.of(request("Zoug", Language.FRENCH, null, Status.OPEN,
                null)));
        String uri = "/admin/requests/" + REQUEST_ID;

        assertThat(mvc.get().uri(uri + "/signsuisse").with(ADMIN).exchange()).bodyText()
                .contains("ZOUG", "action=\"" + uri + "/import\"").doesNotContain("ZURICH");

        MvcTestResult imported = mvc.post().uri(uri + "/import").with(ADMIN).with(csrf())
                .param("uid", FakeSignSuisse.ZOUG.uid()).param("name", "ZOUG").param("language", "fr")
                .param("url", FakeSignSuisse.ZOUG.url()).exchange();

        assertThat(imported).hasRedirectedUrl("/signs/900");
        Sign zoug = search.findByUid("900").orElseThrow();
        assertThat(zoug.definition()).isEqualTo("Ville de Suisse alémanique");
        verify(overrides).save(zoug, ADMIN_ID);
        verify(requests).markAdded(REQUEST_ID, "900");
    }

    @Test
    void signsAlreadyInTheLexiconAreLinkedToTheRequest() {
        assertThat(mvc.post().uri("/admin/requests/" + REQUEST_ID + "/link").with(ADMIN).with(csrf())
                .param("uid", "1").exchange()).hasRedirectedUrl("/admin/requests");

        verify(requests).markAdded(REQUEST_ID, "1");
    }

    @Test
    void administratorsCreateRequestedSigns() {
        when(requests.get(REQUEST_ID)).thenReturn(Optional.of(request("Zoug", Language.GERMAN, null, Status.OPEN,
                null)));
        when(overrides.newUid()).thenReturn("local-1");

        assertThat(mvc.get().uri("/admin/signs/new?request=" + REQUEST_ID).with(ADMIN).exchange())
                .bodyText().contains("value=\"Zoug\"", "name=\"request\"");

        MvcTestResult created = mvc.post().uri("/admin/signs").with(ADMIN).with(csrf())
                .param("name", "ZUG").param("language", "de").param("request", String.valueOf(REQUEST_ID))
                .exchange();

        assertThat(created).hasRedirectedUrl("/signs/local-1");
        verify(overrides).save(any(Sign.class), eq(ADMIN_ID));
        verify(requests).markAdded(REQUEST_ID, "local-1");
    }

    @Test
    void administratorsRejectRequests() {
        mvc.post().uri("/admin/requests/" + REQUEST_ID + "/reject").with(ADMIN).with(csrf()).exchange();

        verify(requests).reject(REQUEST_ID);
    }

    private static SignRequest request(String word, Language language, String comment, Status status,
            String signUid) {
        return new SignRequest(REQUEST_ID, "alice", word, language, comment, status, signUid, Instant.now(),
                status == Status.OPEN ? null : Instant.now());
    }
}
