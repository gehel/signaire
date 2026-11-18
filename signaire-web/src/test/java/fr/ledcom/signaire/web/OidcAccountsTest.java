package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import fr.ledcom.signaire.accounts.Account;
import fr.ledcom.signaire.accounts.AccountService;

class OidcAccountsTest {

    private static final String ISSUER = "https://idp.example.org";

    private final AccountService accounts = mock(AccountService.class);
    private final OidcAccounts oidcAccounts = new OidcAccounts(accounts, new AdminUsers(List.of("alice")));

    @Test
    void logsInWithTheAccountOfTheOidcUser() {
        when(accounts.findOrCreateOidc(ISSUER, "sub-1", "alice", "Alice A."))
                .thenReturn(new Account(42, "alice", "Alice A."));

        OidcAccountUser user = oidcAccounts.toAccountUser(oidcUser(Map.of("preferred_username", "alice",
                "name", "Alice A.")));

        assertThat(user.accountId()).isEqualTo(42);
        assertThat(user.displayName()).isEqualTo("Alice A.");
        assertThat(user.getSubject()).isEqualTo("sub-1");
        assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void usernameDefaultsToTheEmailThenToTheSubject() {
        when(accounts.findOrCreateOidc(ISSUER, "sub-1", "bob", null)).thenReturn(new Account(1, "bob", "bob"));
        when(accounts.findOrCreateOidc(ISSUER, "sub-1", "sub-1", null)).thenReturn(new Account(2, "sub-1", "sub-1"));

        assertThat(oidcAccounts.toAccountUser(oidcUser(Map.of("email", "bob@example.org"))).accountId()).isEqualTo(1);
        assertThat(oidcAccounts.toAccountUser(oidcUser(Map.of())).accountId()).isEqualTo(2);
    }

    private static OidcUser oidcUser(Map<String, Object> claims) {
        Map<String, Object> all = new HashMap<>(claims);
        all.put("iss", ISSUER);
        all.put("sub", "sub-1");
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), all);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token);
    }
}
