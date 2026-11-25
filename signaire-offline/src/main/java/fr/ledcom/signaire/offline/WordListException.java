package fr.ledcom.signaire.offline;

/** A change to word lists that can't be made: the reasons of the web application's lists. */
public final class WordListException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What is wrong. */
    public enum Reason {
        INVALID_NAME, NAME_TAKEN, NOT_FOUND
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
