package fr.ledcom.signaire.bundle;

import static fr.ledcom.signaire.bundle.BundleManifest.CURRENT_FORMAT_VERSION;
import static fr.ledcom.signaire.bundle.BundleManifest.MANIFEST_ENTRY;
import static fr.ledcom.signaire.bundle.BundleManifest.SIGNS_ENTRY;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import fr.ledcom.signaire.model.Sign;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

/** Opens bundles written by {@link BundleWriter}, checking their format version and the integrity of signs. */
public class BundleReader {

    private static final TypeReference<List<Sign>> SIGN_LIST = new TypeReference<>() { };

    /**
     * Opens a bundle. The caller must close the returned bundle, which keeps the file open to serve media.
     */
    @SuppressWarnings("PMD.CloseResource") // closed on failure, otherwise owned by the returned Bundle
    public Bundle read(Path file) {
        ZipFile zip;
        try {
            zip = new ZipFile(file.toFile());
        } catch (IOException e) {
            throw new BundleException("Cannot open bundle " + file, e);
        }
        try {
            BundleManifest manifest = BundleJson.MAPPER.readValue(content(zip, MANIFEST_ENTRY), BundleManifest.class);
            if (manifest.formatVersion() != CURRENT_FORMAT_VERSION) {
                throw new BundleException("Unsupported bundle format version " + manifest.formatVersion()
                        + " in " + file + ", expected " + CURRENT_FORMAT_VERSION);
            }
            byte[] signsJson = content(zip, SIGNS_ENTRY);
            if (!Checksums.sha256(signsJson).equals(manifest.checksums().get(SIGNS_ENTRY))) {
                throw new BundleException("Invalid checksum for " + SIGNS_ENTRY + " in " + file);
            }
            List<Sign> signs = BundleJson.MAPPER.readValue(signsJson, SIGN_LIST);
            return new Bundle(file, zip, manifest, signs);
        } catch (IOException | JacksonException | BundleException e) {
            closeQuietly(zip, e);
            if (e instanceof BundleException invalid) {
                throw invalid;
            }
            throw new BundleException("Invalid bundle " + file, e);
        }
    }

    private static byte[] content(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new BundleException("Missing " + name + " in " + zip.getName());
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static void closeQuietly(Closeable zip, Exception original) {
        try {
            zip.close();
        } catch (IOException e) {
            original.addSuppressed(e);
        }
    }
}
