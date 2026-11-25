package fr.ledcom.signaire.extractor;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/**
 * Where the extraction of a category is: what is done, what is left as far as known, and what each stage does now.
 *
 * @param language the language being searched, null once the search is done
 * @param requestsDone requests sent to SignSuisse so far
 * @param requestsLeft requests known to be left; it grows while the search finds names
 * @param remaining estimate of the time left (requests left at the average pace so far), null before any request
 */
public record ExtractionStatus(Category category, Stage stage, @Nullable Language language, Searches searches, Signs signs,
        Media media, int requestsDone, int requestsLeft, Duration elapsed, @Nullable Duration remaining) {

    /** The stages of an extraction: the search, then the signs and their media (which start during the search). */
    public enum Stage {
        SEARCHING, EXTRACTING, WRITING, DONE
    }

    /**
     * The searches listing the signs.
     *
     * @param pending searches known to be left
     * @param complete false when some prefixes had more names than the search returns but reached the length limit
     * @param current the term searched now, or null
     */
    public record Searches(int sent, int pending, int failed, boolean complete, @Nullable String current) {
    }

    /**
     * The signs found by the searches.
     *
     * @param read signs read from their SignSuisse page
     * @param reused signs taken from the existing bundle of the category
     * @param failed signs whose page could not be read
     * @param current the name of the sign read now, or null
     */
    public record Signs(int found, int read, int reused, int failed, @Nullable String current) {

        /** Signs found but not read, reused or failed yet. */
        public int pending() {
            return found - read - reused - failed;
        }
    }

    /**
     * The media to download (those chosen, not in the media cache yet).
     *
     * @param queued media to download, so far
     * @param failed media that could not be downloaded: they stay linked to SignSuisse
     * @param bytes bytes downloaded
     * @param current the file downloaded now, or null
     */
    public record Media(int queued, int downloaded, int failed, long bytes, @Nullable String current) {

        public int pending() {
            return queued - downloaded - failed;
        }
    }
}
