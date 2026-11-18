package fr.ledcom.signaire.export;

import java.util.Optional;

import fr.ledcom.signaire.model.Media;

/** Reads the content of media, from the bundles where they were downloaded. */
@FunctionalInterface
public interface MediaSource {

    /** No media at all: exports link to SignSuisse instead. */
    MediaSource NONE = media -> Optional.empty();

    /** The content of a media, or empty when it was not downloaded. */
    Optional<byte[]> read(Media media);
}
