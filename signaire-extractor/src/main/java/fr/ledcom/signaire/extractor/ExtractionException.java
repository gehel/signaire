package fr.ledcom.signaire.extractor;

/** SignSuisse data cannot be fetched or does not look as expected. */
public class ExtractionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
