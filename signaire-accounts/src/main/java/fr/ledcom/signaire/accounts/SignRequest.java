package fr.ledcom.signaire.accounts;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import fr.ledcom.signaire.model.Language;

/**
 * A sign that a user asked the administrators to add.
 *
 * @param requester username of the user who asked
 * @param comment why, or what the sign means; may be null
 * @param signUid the sign added for the request, or null
 * @param resolvedAt when the sign was added or the request rejected, or null
 */
public record SignRequest(long id, String requester, String word, Language language, @Nullable String comment,
        Status status, @Nullable String signUid, Instant createdAt, @Nullable Instant resolvedAt) {

    public enum Status {
        OPEN, ADDED, REJECTED
    }
}
