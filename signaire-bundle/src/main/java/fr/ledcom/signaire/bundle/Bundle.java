package fr.ledcom.signaire.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import fr.ledcom.signaire.model.Sign;

/** An open bundle: its manifest, its signs and access to its media. */
public final class Bundle implements AutoCloseable {

    private final Path file;
    private final ZipFile zip;
    private final BundleManifest manifest;
    private final List<Sign> signs;

    Bundle(Path file, ZipFile zip, BundleManifest manifest, Collection<Sign> signs) {
        this.file = file;
        this.zip = zip;
        this.manifest = manifest;
        this.signs = List.copyOf(signs);
    }

    public Path file() {
        return file;
    }

    public BundleManifest manifest() {
        return manifest;
    }

    public List<Sign> signs() {
        return signs;
    }

    /**
     * Opens a media file of the bundle.
     *
     * @param name the {@link fr.ledcom.signaire.model.Media#file()} of the media
     */
    public InputStream openMedia(String name) {
        if (BundleEntries.isReserved(name) || !manifest.checksums().containsKey(name)) {
            throw new BundleException("No media " + name + " in bundle " + file);
        }
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new BundleException("Media " + name + " is listed in the manifest but missing from " + file);
        }
        try {
            return zip.getInputStream(entry);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Checks the checksums of all media files, which {@link BundleReader#read(Path)} does not do. */
    public void verifyMedia() {
        for (Map.Entry<String, String> checksum : manifest.checksums().entrySet()) {
            if (BundleEntries.isReserved(checksum.getKey())) {
                continue;
            }
            try (InputStream in = openMedia(checksum.getKey())) {
                if (!Checksums.sha256(in).equals(checksum.getValue())) {
                    throw new BundleException("Invalid checksum for " + checksum.getKey() + " in " + file);
                }
            } catch (IOException e) {
                throw new BundleException("Cannot read " + checksum.getKey() + " in " + file, e);
            }
        }
    }

    @Override
    public void close() {
        try {
            zip.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
