package fr.ledcom.signaire.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import fr.ledcom.signaire.model.Category;

/** The bundles of a directory: one file per category, named after it. */
public final class BundleFiles {

    private BundleFiles() {
    }

    /** The name of the bundle of a category, e.g. {@code sport-leisure.zip}. */
    public static String fileName(Category category) {
        return category.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".zip";
    }

    /**
     * Stores a bundle in a directory, under the name of its category, once checked (signs and media): an invalid
     * file never replaces a bundle. Uploads of the web application and imports of the Android app go through here.
     *
     * @return the bundle file
     * @throws BundleException if the content is not a valid bundle
     */
    public static Path store(InputStream content, Path directory) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".store-", ".tmp");
        try {
            Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
            Path target;
            try (Bundle bundle = new BundleReader().read(temporary)) {
                bundle.verifyMedia();
                target = directory.resolve(fileName(bundle.manifest().category()));
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
