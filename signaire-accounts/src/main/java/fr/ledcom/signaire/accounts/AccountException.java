package fr.ledcom.signaire.accounts;

/** An account cannot be created as requested. */
public class AccountException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Why, for the messages shown to users. */
    public enum Reason {
        INVALID_USERNAME,
        USERNAME_TAKEN,
        PASSWORD_TOO_SHORT,
    }

    private final Reason reason;

    public AccountException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
