package fr.ledcom.signaire.extractor;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/** Follows an extraction. Called from the threads of its stages: implementations must be safe for concurrent use. */
public interface ExtractionListener {

    ExtractionListener NONE = new ExtractionListener() { };

    /** Where the extraction is: called regularly while it runs, and once it is done. */
    default void progress(ExtractionStatus status) {
    }

    /**
     * A sign is complete: read (or reused), its chosen media downloaded (or failed, then linked), as it will be in the
     * bundle. It can be indexed before the bundle is written.
     */
    default void signExtracted(Sign sign) {
    }

    /** The search found the signs of a language and category. */
    default void enumerated(Category category, Language language, SignEnumerator.Result result) {
    }

    default void signFailed(SearchHit hit, RuntimeException cause) {
    }

    /** A media could not be downloaded: the sign keeps it linked to SignSuisse. */
    default void mediaFailed(Sign sign, Media media, RuntimeException cause) {
    }
}
