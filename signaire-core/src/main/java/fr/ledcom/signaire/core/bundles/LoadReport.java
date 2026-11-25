package fr.ledcom.signaire.core.bundles;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import fr.ledcom.signaire.model.Category;

/**
 * Outcome of loading a bundle.
 *
 * @param file the bundle file
 * @param category the category of the bundle, {@code null} if it could not be read
 * @param signCount number of signs in the bundle
 * @param status what happened
 * @param error why loading failed, {@code null} otherwise
 */
public record LoadReport(Path file, @Nullable Category category, int signCount, Status status,
        @Nullable String error) {

    /** What happened to a bundle. */
    public enum Status {
        /** The signs of the bundle were indexed. */
        LOADED,
        /** The bundle was already loaded, with the same signs. */
        UNCHANGED,
        /** The bundle could not be loaded. */
        FAILED
    }

    static LoadReport failed(Path file, @Nullable String error) {
        return new LoadReport(file, null, 0, Status.FAILED, error);
    }
}
