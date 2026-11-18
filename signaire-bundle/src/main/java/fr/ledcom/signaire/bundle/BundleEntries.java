package fr.ledcom.signaire.bundle;

import static fr.ledcom.signaire.bundle.BundleManifest.MANIFEST_ENTRY;
import static fr.ledcom.signaire.bundle.BundleManifest.SIGNS_ENTRY;

/** Rules on the names of bundle entries. */
final class BundleEntries {

    private BundleEntries() {
    }

    /** Whether {@code name} is an entry with a special meaning, which cannot be used for media. */
    static boolean isReserved(String name) {
        return MANIFEST_ENTRY.equals(name) || SIGNS_ENTRY.equals(name);
    }
}
