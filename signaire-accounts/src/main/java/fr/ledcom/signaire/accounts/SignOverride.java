package fr.ledcom.signaire.accounts;

import java.time.Instant;

import fr.ledcom.signaire.model.Sign;

/**
 * A change of an administrator to the signs of the bundles.
 *
 * @param uid the sign changed
 * @param sign the sign as edited or added, {@code null} when deleted
 * @param deleted whether the sign was deleted
 * @param updatedAt when the change was made
 * @param updatedBy username of the administrator who made it, {@code null} when the account was deleted
 */
public record SignOverride(String uid, Sign sign, boolean deleted, Instant updatedAt, String updatedBy) {
}
