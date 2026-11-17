package fr.ledcom.signaire.extractor;

import java.util.Objects;

import fr.ledcom.signaire.model.Language;

/**
 * A result of the SignSuisse search.
 *
 * @param uid SignSuisse identifier of the sign
 * @param name name of the sign
 * @param language language of the sign
 * @param url the page of the sign
 */
public record SearchHit(String uid, String name, Language language, String url) {

    public SearchHit {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(url, "url");
    }
}
