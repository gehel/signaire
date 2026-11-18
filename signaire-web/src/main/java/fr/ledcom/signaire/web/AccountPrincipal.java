package fr.ledcom.signaire.web;

/** The logged in user, whatever the way of logging in: {@code @AuthenticationPrincipal AccountPrincipal user}. */
public interface AccountPrincipal {

    long accountId();

    String displayName();
}
