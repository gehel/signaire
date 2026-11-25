package fr.ledcom.signaire.model;

import org.jspecify.annotations.Nullable;

/**
 * An example sentence using a sign.
 *
 * @param text the sentence, in the written language matching the sign language, or {@code null} when SignSuisse
 *             only signs it
 * @param video the sentence signed, or {@code null}
 */
public record Example(@Nullable String text, @Nullable Media video) {
}
