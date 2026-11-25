package fr.ledcom.signaire.accounts;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and finds accounts: local accounts with a password, and accounts of OpenID Connect users. */
@Service
@Transactional
public class AccountService {

    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,40}");
    private static final Pattern INVALID_USERNAME_CHARACTERS = Pattern.compile("[^a-z0-9._-]+");
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    /** Room for a "-123" suffix in generated usernames. */
    private static final int MAX_GENERATED_USERNAME_BASE = 36;

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a local account.
     *
     * @param username 3 to 40 letters, digits, dots, dashes or underscores; stored in lower case
     * @param displayName name shown in the UI, the username when blank
     * @throws AccountException if the username is invalid or taken, or the password too short
     */
    public Account register(@Nullable String username, @Nullable String password, @Nullable String displayName) {
        String normalized = normalizeUsername(username);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new AccountException(AccountException.Reason.INVALID_USERNAME, "Invalid username: " + username);
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new AccountException(AccountException.Reason.PASSWORD_TOO_SHORT, "Password too short");
        }
        if (repository.existsByUsername(normalized)) {
            throw new AccountException(AccountException.Reason.USERNAME_TAKEN, "Username taken: " + normalized);
        }
        AccountEntity account = new AccountEntity(normalized, displayName(displayName, normalized), Instant.now());
        account.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(password), "password hash"));
        return repository.save(account).toAccount();
    }

    @Transactional(readOnly = true)
    public Optional<LocalCredentials> findLocalCredentials(String username) {
        return repository.findByUsername(normalizeUsername(username))
                .flatMap(account -> Optional.ofNullable(account.passwordHash())
                        .map(hash -> new LocalCredentials(account.id(), account.username(),
                                account.toAccount().displayName(), hash)));
    }

    @Transactional(readOnly = true)
    public Optional<Account> findById(long id) {
        return repository.findById(id).map(AccountEntity::toAccount);
    }

    /**
     * Finds the account of an OpenID Connect user, creating it on the first login. The display name follows
     * the identity provider.
     *
     * @param preferredUsername the username to give to a new account, if it's free
     */
    public Account findOrCreateOidc(String issuer, String subject, String preferredUsername,
            @Nullable String displayName) {
        AccountEntity account = repository.findByOidcIssuerAndOidcSubject(issuer, subject).orElseGet(() -> {
            String username = freeUsername(preferredUsername);
            AccountEntity created = new AccountEntity(username, username, Instant.now());
            created.setOidcIdentity(issuer, subject);
            return created;
        });
        account.setDisplayName(displayName(displayName == null ? preferredUsername : displayName, account.username()));
        return repository.save(account).toAccount();
    }

    private String freeUsername(String preferred) {
        String base = INVALID_USERNAME_CHARACTERS.matcher(normalizeUsername(preferred)).replaceAll("-");
        if (base.length() > MAX_GENERATED_USERNAME_BASE) {
            base = base.substring(0, MAX_GENERATED_USERNAME_BASE);
        }
        if (base.length() < 3) {
            base = "user";
        }
        String username = base;
        for (int suffix = 2; repository.existsByUsername(username); suffix++) {
            username = base + "-" + suffix;
        }
        return username;
    }

    private static String normalizeUsername(@Nullable String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private static String displayName(@Nullable String displayName, String username) {
        if (displayName == null || displayName.isBlank()) {
            return username;
        }
        String stripped = displayName.strip();
        return stripped.length() > MAX_DISPLAY_NAME_LENGTH ? stripped.substring(0, MAX_DISPLAY_NAME_LENGTH) : stripped;
    }
}
