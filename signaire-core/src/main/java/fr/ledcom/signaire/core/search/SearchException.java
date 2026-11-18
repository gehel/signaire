package fr.ledcom.signaire.core.search;

/** The index cannot be read or written. */
public class SearchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
