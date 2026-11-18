package fr.ledcom.signaire.web;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import fr.ledcom.signaire.accounts.Account;

/** A user logged in with OpenID Connect, and their Signaire account. */
public class OidcAccountUser extends DefaultOidcUser implements AccountPrincipal {

    private static final long serialVersionUID = 1L;

    private final long accountId;
    private final String displayName;

    OidcAccountUser(OidcUser user, Collection<? extends GrantedAuthority> authorities, Account account) {
        super(authorities, user.getIdToken(), user.getUserInfo());
        this.accountId = account.id();
        this.displayName = account.displayName();
    }

    @Override
    public long accountId() {
        return accountId;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OidcAccountUser user && accountId == user.accountId && super.equals(other);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Long.hashCode(accountId);
    }
}
