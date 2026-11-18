package fr.ledcom.signaire.accounts;

/**
 * What is needed to check the password of a local account.
 *
 * @param accountId identifier of the account
 * @param username unique login name, lower case
 * @param displayName name shown in the UI
 * @param passwordHash the password, hashed by the {@link org.springframework.security.crypto.password.PasswordEncoder}
 */
public record LocalCredentials(long accountId, String username, String displayName, String passwordHash) {
}
