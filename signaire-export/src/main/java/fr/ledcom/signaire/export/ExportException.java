package fr.ledcom.signaire.export;

public class ExportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
