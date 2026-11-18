package fr.ledcom.signaire.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleReader;
import fr.ledcom.signaire.extractor.CategoryExtractor;

/**
 * Stores uploaded bundles in the bundle directory, under the name of their category, once checked: an invalid
 * file never replaces a bundle.
 */
@Component
public class BundleUploads {

    private final Path bundles;
    private final BundleReader reader = new BundleReader();

    public BundleUploads(@Value("${signaire.bundles.directory:bundles}") Path bundles) {
        this.bundles = bundles;
    }

    /**
     * @return the bundle file
     * @throws BundleException if the upload is not a valid bundle
     */
    public Path store(InputStream upload) throws IOException {
        Files.createDirectories(bundles);
        Path temporary = Files.createTempFile(bundles, ".upload-", ".tmp");
        try {
            Files.copy(upload, temporary, StandardCopyOption.REPLACE_EXISTING);
            Path target;
            try (Bundle bundle = reader.read(temporary)) {
                bundle.verifyMedia();
                target = bundles.resolve(CategoryExtractor.bundleFileName(bundle.manifest().category()));
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
