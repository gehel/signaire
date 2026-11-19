package fr.ledcom.signaire.extractor;

import java.net.URI;

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

    /** The details of a sign were read: its media not downloaded yet are downloaded before it is extracted. */
    default void mediaQueued(Sign sign, int count) {
    }

    default void mediaDownloaded(URI uri) {
    }

    /** A sign was extracted, with its media when they are downloaded. */
    default void signExtracted(Sign sign) {
    }

    default void signFailed(SearchHit hit, RuntimeException cause) {
    }
}
