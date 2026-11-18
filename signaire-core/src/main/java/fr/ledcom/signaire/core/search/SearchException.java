package fr.ledcom.signaire.core.search;

/** Elasticsearch cannot be reached or refused a request. */
public class SearchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
