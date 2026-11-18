package fr.ledcom.signaire.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import fr.ledcom.signaire.model.Language;

// Hibernate checks that the entities match the schema made by Flyway
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(PostgresTestConfiguration.class)
class SignRequestServiceIT {

    @Autowired
    private AccountService accounts;

    @Autowired
    private SignRequestService requests;

    @Autowired
    private AccountsCleaner cleaner;

    private long alice;
    private long bob;

    @BeforeEach
    void createAccounts() {
        cleaner.deleteAll();
        alice = accounts.register("alice", "correct horse", null).id();
        bob = accounts.register("bob", "correct horse", null).id();
    }

    @Test
    void usersRequestSigns() {
        SignRequest request = requests.request(alice, "  Zoug ", Language.FRENCH, "Le canton");

        assertThat(request.word()).isEqualTo("Zoug");
        assertThat(request.language()).isEqualTo(Language.FRENCH);
        assertThat(request.comment()).isEqualTo("Le canton");
        assertThat(request.requester()).isEqualTo("alice");
        assertThat(request.status()).isEqualTo(SignRequest.Status.OPEN);
        assertThat(request.createdAt()).isNotNull();
    }

    @Test
    void usersSeeTheirOwnRequestsOnly() {
        requests.request(alice, "Zoug", Language.FRENCH, null);
        requests.request(alice, "Uri", Language.GERMAN, "");
        requests.request(bob, "Sion", Language.FRENCH, null);

        assertThat(requests.byRequester(alice)).extracting(SignRequest::word).containsExactly("Uri", "Zoug");
    }

    @Test
    void administratorsSeeTheOpenRequestsOldestFirst() {
        SignRequest zoug = requests.request(alice, "Zoug", Language.FRENCH, null);
        SignRequest sion = requests.request(bob, "Sion", Language.FRENCH, null);
        requests.request(bob, "Uri", Language.GERMAN, null);
        requests.reject(sion.id());

        assertThat(requests.open()).extracting(SignRequest::word).containsExactly("Zoug", "Uri");
        assertThat(requests.openCount()).isEqualTo(2);
        assertThat(requests.get(zoug.id())).contains(zoug);
    }

    @Test
    void requestsAreResolvedByAddingOrRejecting() {
        SignRequest zoug = requests.request(alice, "Zoug", Language.FRENCH, null);
        SignRequest sion = requests.request(alice, "Sion", Language.FRENCH, null);

        requests.markAdded(zoug.id(), "900");
        requests.reject(sion.id());

        assertThat(requests.get(zoug.id()).orElseThrow()).satisfies(added -> {
            assertThat(added.status()).isEqualTo(SignRequest.Status.ADDED);
            assertThat(added.signUid()).isEqualTo("900");
            assertThat(added.resolvedAt()).isNotNull();
        });
        assertThat(requests.get(sion.id()).orElseThrow().status()).isEqualTo(SignRequest.Status.REJECTED);
    }

    @Test
    void rejectsEmptyWords() {
        assertThatThrownBy(() -> requests.request(alice, " ", Language.FRENCH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
