package fr.ledcom.signaire.bundle;

/** A bundle cannot be written or read, or its content is invalid. */
public class BundleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BundleException(String message) {
        super(message);
    }

    public BundleException(String message, Throwable cause) {
        super(message, cause);
    }
}
