package fr.ledcom.signaire.extractor;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/** Follows the progress of an extraction. */
public interface ExtractionListener {

    ExtractionListener NONE = new ExtractionListener() { };

    /** A search for the signs of a language and category, which takes hundreds of them, was sent. */
    default void searched(Category category, Language language, int queries, int found) {
    }

    /** The search found the signs of a language and category. */
    default void enumerated(Category category, Language language, SignEnumerator.Result result) {
    }

    default void signExtracted(Sign sign) {
    }

    default void signFailed(SearchHit hit, RuntimeException cause) {
    }
}
