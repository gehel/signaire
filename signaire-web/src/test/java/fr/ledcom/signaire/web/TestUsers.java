package fr.ledcom.signaire.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import fr.ledcom.signaire.accounts.LocalCredentials;

/** Logged-in users of the tests of the web layer, whose accounts are mocks: their ids are fixed. */
final class TestUsers {

    static final long ALICE_ID = 1;
    static final long BOB_ID = 2;
    /** An administrator: {@code signaire.accounts.admins} of the test configuration. */
    static final long ADMIN_ID = 3;

    static final RequestPostProcessor ALICE = as(ALICE_ID, "alice");
    static final RequestPostProcessor BOB = as(BOB_ID, "bob");
    static final RequestPostProcessor ADMIN = user(new LocalUser(credentials(ADMIN_ID, "admin"),
            new AdminUsers(List.of("admin"))));

    private TestUsers() {
    }

    private static RequestPostProcessor as(long accountId, String username) {
        return user(new LocalUser(credentials(accountId, username), new AdminUsers(List.of())));
    }

    private static LocalCredentials credentials(long accountId, String username) {
        return new LocalCredentials(accountId, username, null, "{noop}correct horse");
    }
}
