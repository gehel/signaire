package fr.ledcom.signaire.accounts;

/** A word list operation is not possible. */
public class WordListException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Why, for the messages shown to users. */
    public enum Reason {
        /** The list doesn't exist, or belongs to another account. */
        NOT_FOUND,
        INVALID_NAME,
        NAME_TAKEN,
    }

    private final Reason reason;

    public WordListException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
