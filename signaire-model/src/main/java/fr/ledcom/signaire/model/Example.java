package fr.ledcom.signaire.model;

/**
 * An example sentence using a sign.
 *
 * @param text the sentence, in the written language matching the sign language
 * @param video the sentence signed, or {@code null}
 */
public record Example(String text, Media video) {
}
