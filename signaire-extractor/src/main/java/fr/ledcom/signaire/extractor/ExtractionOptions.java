package fr.ledcom.signaire.extractor;

/**
 * Choices of an extraction.
 *
 * @param media the media to download into the bundle; the others are only referenced by their URLs
 * @param refresh read the page of every sign, rather than reusing the signs of the existing bundle
 */
public record ExtractionOptions(MediaChoice media, boolean refresh) {

    public static ExtractionOptions of(MediaChoice media) {
        return new ExtractionOptions(media, false);
    }
}
