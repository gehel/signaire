package fr.ledcom.signaire.core.bundles;

import fr.ledcom.signaire.model.Category;

/**
 * A loaded bundle, as recorded in the {@link fr.ledcom.signaire.core.search.SignIndex#BUNDLES} index.
 *
 * @param file name of the bundle file
 * @param category category of the bundle
 * @param checksum checksum of the signs of the bundle, which identifies its content
 * @param signCount number of signs
 * @param loadedAt when the bundle was loaded (ISO-8601)
 */
public record BundleRecord(String file, Category category, String checksum, int signCount, String loadedAt) {
}
