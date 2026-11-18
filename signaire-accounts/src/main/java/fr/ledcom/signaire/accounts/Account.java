package fr.ledcom.signaire.accounts;

/**
 * A user account.
 *
 * @param id identifier of the account
 * @param username unique login name, lower case
 * @param displayName name shown in the UI
 */
public record Account(long id, String username, String displayName) {
}
