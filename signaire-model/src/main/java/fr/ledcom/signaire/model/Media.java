package fr.ledcom.signaire.model;

import java.util.Objects;

/**
 * A video or image of a sign.
 *
 * @param sourceUrl where SignSuisse publishes the media
 * @param file path of the media inside its bundle, or {@code null} when it was not downloaded
 */
public record Media(String sourceUrl, String file) {

    public Media {
        Objects.requireNonNull(sourceUrl, "sourceUrl");
    }
}
