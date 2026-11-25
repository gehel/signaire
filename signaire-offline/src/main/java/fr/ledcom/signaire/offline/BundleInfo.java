package fr.ledcom.signaire.offline;

import java.nio.file.Path;
import java.time.Instant;

import fr.ledcom.signaire.model.Category;

/**
 * A bundle of the library.
 *
 * @param createdAt when it was extracted
 */
public record BundleInfo(Path file, Category category, int signCount, Instant createdAt) {
}
