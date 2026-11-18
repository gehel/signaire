package fr.ledcom.signaire.export;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import fr.ledcom.signaire.model.Sign;

/**
 * What to export.
 *
 * @param title title of the document (a list name, a search…)
 * @param signs the signs, in order
 * @param locale language of the labels of the document (French, German, Italian or English)
 */
public record ExportRequest(String title, List<Sign> signs, Locale locale) {

    public ExportRequest {
        Objects.requireNonNull(title, "title");
        signs = List.copyOf(signs);
        Objects.requireNonNull(locale, "locale");
    }
}
