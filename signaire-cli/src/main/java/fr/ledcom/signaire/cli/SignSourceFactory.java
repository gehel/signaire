package fr.ledcom.signaire.cli;

import java.time.Duration;

import fr.ledcom.signaire.extractor.SignSource;

/** Creates the source signs are extracted from. */
@FunctionalInterface
public interface SignSourceFactory {

    /**
     * @param interval minimum delay between two requests to the source
     */
    SignSource create(Duration interval);
}
