package fr.ledcom.signaire.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

// Hibernate checks that the entities match the schema made by Flyway
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@H2Database
class AccountServiceTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountsCleaner cleaner;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void clean() {
        cleaner.deleteAll();
    }

    /** On H2, as the standalone application; PostgreSQL is tested in signaire-it, with the web application. */
    @Test
    void runsOnH2() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }

    @Test
    void registersLocalAccountsWithHashedPasswords() {
        Account account = accounts.register("Alice", "correct horse", "Alice A.");

        assertThat(account.username()).isEqualTo("alice");
        assertThat(account.displayName()).isEqualTo("Alice A.");
        LocalCredentials credentials = accounts.findLocalCredentials("ALICE").orElseThrow();
        assertThat(credentials.accountId()).isEqualTo(account.id());
        assertThat(credentials.displayName()).isEqualTo("Alice A.");
        assertThat(credentials.passwordHash()).doesNotContain("correct horse");
        assertThat(passwordEncoder.matches("correct horse", credentials.passwordHash())).isTrue();
    }

    @Test
    void displayNameDefaultsToTheUsername() {
        assertThat(accounts.register("bob", "correct horse", " ").displayName()).isEqualTo("bob");
    }

    @Test
    void usernamesAreUnique() {
        accounts.register("alice", "correct horse", null);

        assertThatThrownBy(() -> accounts.register("Alice", "other password", null))
                .isInstanceOfSatisfying(AccountException.class,
                        e -> assertThat(e.reason()).isEqualTo(AccountException.Reason.USERNAME_TAKEN));
    }

    @Test
    void rejectsInvalidUsernames() {
        for (String username : new String[] {"", "al", "alice smith", "a".repeat(41), "élise"}) {
            assertThatThrownBy(() -> accounts.register(username, "correct horse", null))
                    .as(username)
                    .isInstanceOfSatisfying(AccountException.class,
                            e -> assertThat(e.reason()).isEqualTo(AccountException.Reason.INVALID_USERNAME));
        }
    }

    @Test
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> accounts.register("alice", "short", null))
                .isInstanceOfSatisfying(AccountException.class,
                        e -> assertThat(e.reason()).isEqualTo(AccountException.Reason.PASSWORD_TOO_SHORT));
    }

    @Test
    void unknownUsersHaveNoCredentials() {
        assertThat(accounts.findLocalCredentials("nobody")).isEmpty();
    }

    @Test
    void createsAnAccountOnTheFirstOidcLogin() {
        Account first = accounts.findOrCreateOidc("https://idp.example.org", "sub-1", "alice", "Alice A.");
        Account again = accounts.findOrCreateOidc("https://idp.example.org", "sub-1", "alice", "Alice B.");

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.displayName()).isEqualTo("Alice B.");
        assertThat(accounts.findLocalCredentials(first.username())).isEmpty();
    }

    @Test
    void oidcAccountsGetAFreeUsername() {
        Account local = accounts.register("alice", "correct horse", null);

        Account oidc = accounts.findOrCreateOidc("https://idp.example.org", "sub-1", "Alice", null);

        assertThat(oidc.id()).isNotEqualTo(local.id());
        assertThat(oidc.username()).isEqualTo("alice-2");
        assertThat(oidc.displayName()).isEqualTo("Alice");
    }

    @Test
    void findsAccountsById() {
        Account account = accounts.register("alice", "correct horse", null);

        assertThat(accounts.findById(account.id())).contains(account);
        assertThat(accounts.findById(-1)).isEmpty();
    }
}
