package fr.ledcom.signaire.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import fr.ledcom.signaire.accounts.Account;
import fr.ledcom.signaire.accounts.AccountService;

/**
 * Gives users logged in with OpenID Connect their Signaire account, created on their first login and
 * identified by the issuer and subject of their identity.
 */
public class OidcAccounts implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final AccountService accounts;
    private final AdminUsers admins;

    public OidcAccounts(AccountService accounts, AdminUsers admins) {
        this.accounts = accounts;
        this.admins = admins;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        return toAccountUser(Objects.requireNonNull(delegate.loadUser(request), "OIDC user"));
    }

    OidcAccountUser toAccountUser(OidcUser user) {
        Account account = accounts.findOrCreateOidc(String.valueOf(user.getIssuer()), subject(user),
                username(user), user.getFullName());
        List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
        authorities.addAll(admins.authorities(account.username()));
        return new OidcAccountUser(user, authorities, account);
    }

    /** The username to give to a new account: the preferred one, else from the email address, else the subject. */
    private static String username(OidcUser user) {
        String preferred = user.getPreferredUsername();
        if (preferred != null) {
            return preferred;
        }
        String email = user.getEmail();
        // > 0: an address starting with @ has no name before it
        if (email != null && email.indexOf('@') > 0) { // NOSONAR
            return email.substring(0, email.indexOf('@'));
        }
        return subject(user);
    }

    private static String subject(OidcUser user) {
        return Objects.requireNonNull(user.getSubject(), "subject, mandatory in OpenID Connect");
    }
}
