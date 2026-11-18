package fr.ledcom.signaire.bundle;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import fr.ledcom.signaire.model.Category;

/**
 * Description of a bundle, stored as {@value #MANIFEST_ENTRY} at the root of the bundle.
 *
 * @param formatVersion version of the bundle format
 * @param category the category whose signs the bundle contains
 * @param createdAt when the bundle was written
 * @param signCount number of signs in the bundle
 * @param checksums SHA-256 (hex) of each entry of the bundle except the manifest, by entry name
 */
public record BundleManifest(
        int formatVersion,
        Category category,
        Instant createdAt,
        int signCount,
        Map<String, String> checksums) {

    public static final int CURRENT_FORMAT_VERSION = 1;
    public static final String MANIFEST_ENTRY = "manifest.json";
    public static final String SIGNS_ENTRY = "signs.json";

    public BundleManifest {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(createdAt, "createdAt");
        // sorted, so that the serialized manifest is deterministic
        checksums = checksums == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(checksums));
    }
}
