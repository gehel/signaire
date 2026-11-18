package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.AccountsCleaner;
import fr.ledcom.signaire.accounts.SignOverrideService;
import fr.ledcom.signaire.accounts.SignRequest;
import fr.ledcom.signaire.accounts.SignRequestService;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Language;

@WebIntegrationTest
class SignRequestsIT {

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
    private SignRequestService requests;

    @Autowired
    private SignOverrideService overrides;

    @Autowired
    private AdminUsers admins;

    private LocalUser admin;
    private LocalUser alice;

    @BeforeEach
    void setUp() {
        cleaner.deleteAll();
        index.recreate();
        index.index(TestSigns.ALL);
        index.refresh();
        accounts.register("admin", "correct horse", "Admin");
        accounts.register("alice", "correct horse", "Alice");
        admin = new LocalUser(accounts.findLocalCredentials("admin").orElseThrow(), admins);
        alice = new LocalUser(accounts.findLocalCredentials("alice").orElseThrow(), admins);
    }

    @Test
    void usersRequestMissingSigns() {
        assertThat(mvc.get().uri("/?q=zoug").with(user(alice)).exchange()).bodyText()
                .contains("href=\"/requests?word=zoug\"");
        assertThat(mvc.get().uri("/requests?word=zoug").with(user(alice)).exchange()).bodyText()
                .contains("value=\"zoug\"");

        MvcTestResult sent = mvc.post().uri("/requests").with(user(alice)).with(csrf())
                .param("word", "Zoug").param("language", "fr").param("comment", "Le canton").exchange();

        assertThat(sent).hasRedirectedUrl("/requests");
        assertThat(sent).flash().containsEntry("status", "Demande envoyée : « Zoug ».");
        assertThat(mvc.get().uri("/requests").with(user(alice)).exchange()).bodyText()
                .contains("Zoug", "Le canton", "En attente");
    }

    @Test
    void requestsNeedAWord() {
        assertThat(mvc.post().uri("/requests").with(user(alice)).with(csrf())
                .param("word", " ").param("language", "fr").exchange()).bodyText()
                .contains("Le mot est obligatoire");
    }

    @Test
    void requestsNeedAnAccount() {
        assertThat(mvc.get().uri("/requests").exchange()).hasStatus(HttpStatus.FOUND);
    }

    @Test
    void administratorsSeeTheOpenRequests() {
        requests.request(alice.accountId(), "Zoug", Language.FRENCH, "Le canton");

        assertThat(mvc.get().uri("/admin").with(user(admin)).exchange()).bodyText()
                .contains("href=\"/admin/requests\"", "1 demande en attente");
        assertThat(mvc.get().uri("/admin/requests").with(user(admin)).exchange()).bodyText()
                .contains("Zoug", "alice", "Le canton");
    }

    @Test
    void administratorsImportRequestedSignsFromSignSuisse() {
        SignRequest request = requests.request(alice.accountId(), "Zoug", Language.FRENCH, null);
        String uri = "/admin/requests/" + request.id();

        assertThat(mvc.get().uri(uri + "/signsuisse").with(user(admin)).exchange()).bodyText()
                .contains("ZOUG", "action=\"" + uri + "/import\"").doesNotContain("ZURICH");

        MvcTestResult imported = mvc.post().uri(uri + "/import").with(user(admin)).with(csrf())
                .param("uid", FakeSignSuisse.ZOUG.uid()).param("name", "ZOUG").param("language", "fr")
                .param("url", FakeSignSuisse.ZOUG.url()).exchange();

        assertThat(imported).hasRedirectedUrl("/signs/900");
        assertThat(search.findByUid("900").orElseThrow().definition()).isEqualTo("Ville de Suisse alémanique");
        assertThat(overrides.find("900")).isPresent();
        assertThat(requests.get(request.id()).orElseThrow().status()).isEqualTo(SignRequest.Status.ADDED);
        assertThat(mvc.get().uri("/requests").with(user(alice)).exchange()).bodyText()
                .contains("Ajouté", "href=\"/signs/900\"");
    }

    @Test
    void signsAlreadyInTheLexiconAreLinkedToTheRequest() {
        index.index(List.of(TestSigns.LAUSANNE.toBuilder().build()));
        SignRequest request = requests.request(alice.accountId(), "Lausanne", Language.FRENCH, null);

        assertThat(mvc.post().uri("/admin/requests/" + request.id() + "/link").with(user(admin)).with(csrf())
                .param("uid", "1").exchange()).hasRedirectedUrl("/admin/requests");

        assertThat(requests.get(request.id()).orElseThrow().signUid()).isEqualTo("1");
    }

    @Test
    void administratorsCreateRequestedSigns() {
        SignRequest request = requests.request(alice.accountId(), "Zoug", Language.GERMAN, null);

        assertThat(mvc.get().uri("/admin/signs/new?request=" + request.id()).with(user(admin)).exchange())
                .bodyText().contains("value=\"Zoug\"", "name=\"request\"");

        MvcTestResult created = mvc.post().uri("/admin/signs").with(user(admin)).with(csrf())
                .param("name", "ZUG").param("language", "de").param("request", String.valueOf(request.id()))
                .exchange();

        String uid = created.getResponse().getRedirectedUrl().substring("/signs/".length());
        assertThat(requests.get(request.id()).orElseThrow().signUid()).isEqualTo(uid);
    }

    @Test
    void administratorsRejectRequests() {
        SignRequest request = requests.request(alice.accountId(), "Xyz", Language.FRENCH, null);

        mvc.post().uri("/admin/requests/" + request.id() + "/reject").with(user(admin)).with(csrf()).exchange();

        assertThat(requests.get(request.id()).orElseThrow().status()).isEqualTo(SignRequest.Status.REJECTED);
        assertThat(mvc.get().uri("/requests").with(user(alice)).exchange()).bodyText().contains("Refusé");
    }
}
