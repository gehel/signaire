package fr.ledcom.signaire.accounts;

import java.time.Instant;

import fr.ledcom.signaire.model.Language;

/**
 * A sign that a user asked the administrators to add.
 *
 * @param requester username of the user who asked
 * @param comment why, or what the sign means; may be null
 * @param signUid the sign added for the request, or null
 * @param resolvedAt when the sign was added or the request rejected, or null
 */
public record SignRequest(long id, String requester, String word, Language language, String comment,
        Status status, String signUid, Instant createdAt, Instant resolvedAt) {

    /** Where a request is. */
    public enum Status {
        OPEN, ADDED, REJECTED
    }
}
