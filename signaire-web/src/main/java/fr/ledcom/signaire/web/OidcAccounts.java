package fr.ledcom.signaire.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import fr.ledcom.signaire.accounts.AccountService;

/**
 * Gives users logged in with OpenID Connect their Signaire account, created on their first login and
 * identified by the issuer and subject of their identity.
 */
public class OidcAccounts implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final AccountService accounts;

    public OidcAccounts(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        return toAccountUser(Objects.requireNonNull(delegate.loadUser(request), "OIDC user"));
    }

    OidcAccountUser toAccountUser(OidcUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        return new OidcAccountUser(user, authorities, accounts.findOrCreateOidc(String.valueOf(user.getIssuer()),
                user.getSubject(), username(user), user.getFullName()));
    }

    /** The username to give to a new account: the preferred one, else from the email address, else the subject. */
    private static String username(OidcUser user) {
        if (user.getPreferredUsername() != null) {
            return user.getPreferredUsername();
        }
        String email = user.getEmail();
        if (email != null && email.indexOf('@') > 0) {
            return email.substring(0, email.indexOf('@'));
        }
        return user.getSubject();
    }
}
