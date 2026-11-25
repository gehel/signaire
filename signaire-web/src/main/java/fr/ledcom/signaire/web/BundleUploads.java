package fr.ledcom.signaire.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleFiles;

/**
 * Stores uploaded bundles in the bundle directory, under the name of their category, once checked: an invalid
 * file never replaces a bundle.
 */
@Component
public class BundleUploads {

    private final Path bundles;

    public BundleUploads(@Value("${signaire.bundles.directory:bundles}") Path bundles) {
        this.bundles = bundles;
    }

    /**
     * @return the bundle file
     * @throws BundleException if the upload is not a valid bundle
     */
    public Path store(InputStream upload) throws IOException {
        return BundleFiles.store(upload, bundles);
    }
}
