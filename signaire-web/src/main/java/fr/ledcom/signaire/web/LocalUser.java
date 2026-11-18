package fr.ledcom.signaire.web;

import java.util.Objects;

import org.springframework.security.core.userdetails.User;

import fr.ledcom.signaire.accounts.LocalCredentials;

/** A user logged in with a username and password. */
public class LocalUser extends User implements AccountPrincipal {

    private static final long serialVersionUID = 1L;

    private final long accountId;
    private final String displayName;

    LocalUser(LocalCredentials credentials, AdminUsers admins) {
        super(credentials.username(), credentials.passwordHash(), admins.authorities(credentials.username()));
        this.accountId = credentials.accountId();
        this.displayName = credentials.displayName();
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
        return other instanceof LocalUser user && accountId == user.accountId && super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), accountId);
    }
}
