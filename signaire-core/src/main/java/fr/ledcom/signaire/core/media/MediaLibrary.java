package fr.ledcom.signaire.core.media;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleManifest;
import fr.ledcom.signaire.bundle.BundleReader;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * The media (videos, images) of the bundles of a directory, read in place from the bundles. Bundles added,
 * replaced or removed are noticed on {@link #refresh()}, and when a media is not found.
 */
@Service
@ThreadSafe
public class MediaLibrary implements AutoCloseable {

    private static final Logger LOG = System.getLogger(MediaLibrary.class.getName());
    private static final String BUNDLE_EXTENSION = ".zip";

    private final Path directory;
    private final BundleReader reader = new BundleReader();
    /** The bundles read so far, by file. */
    @GuardedBy("this")
    private final Map<Path, OpenBundle> bundles = new HashMap<>();
    /** The bundle holding each media. */
    @GuardedBy("this")
    private Map<String, OpenBundle> bundleByMedia = Map.of();

    public MediaLibrary(@Value("${signaire.bundles.directory:bundles}") Path directory) {
        this.directory = directory;
    }

    /** A bundle opened as a file system, and the version of its file. */
    private record OpenBundle(FileSystem files, List<String> media, FileTime modified, long size) {
        boolean isCurrent(Path file) throws IOException {
            return modified.equals(Files.getLastModifiedTime(file)) && size == Files.size(file);
        }
    }

    /**
     * Finds a media.
     *
     * @param file the {@link fr.ledcom.signaire.model.Media#file()} of the media
     */
    public Optional<Resource> find(String file) {
        OpenBundle bundle = bundleFor(file);
        if (bundle == null) {
            refresh();
            bundle = bundleFor(file);
        }
        return bundle == null ? Optional.empty() : Optional.of(new FileSystemResource(bundle.files().getPath(file)));
    }

    private synchronized OpenBundle bundleFor(String file) {
        return bundleByMedia.get(file);
    }

    /** Takes into account the bundles added, replaced or removed since the last refresh. */
    public synchronized void refresh() {
        List<Path> files = bundleFiles();
        bundles.entrySet().removeIf(bundle -> {
            boolean stale = !files.contains(bundle.getKey()) || !isCurrent(bundle.getKey(), bundle.getValue());
            if (stale) {
                closeQuietly(bundle.getValue());
            }
            return stale;
        });
        for (Path file : files) {
            if (!bundles.containsKey(file)) {
                open(file).ifPresent(bundle -> bundles.put(file, bundle));
            }
        }
        Map<String, OpenBundle> byMedia = new HashMap<>();
        for (OpenBundle bundle : bundles.values()) {
            bundle.media().forEach(media -> byMedia.putIfAbsent(media, bundle));
        }
        bundleByMedia = Map.copyOf(byMedia);
    }

    private List<Path> bundleFiles() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> String.valueOf(file.getFileName()).endsWith(BUNDLE_EXTENSION))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot list the bundles of " + directory, e);
            return List.of();
        }
    }

    private Optional<OpenBundle> open(Path file) {
        try {
            FileTime modified = Files.getLastModifiedTime(file);
            long size = Files.size(file);
            List<String> media;
            try (Bundle bundle = reader.read(file)) {
                media = bundle.manifest().checksums().keySet().stream()
                        .filter(name -> !BundleManifest.MANIFEST_ENTRY.equals(name))
                        .filter(name -> !BundleManifest.SIGNS_ENTRY.equals(name))
                        .toList();
            }
            return Optional.of(new OpenBundle(FileSystems.newFileSystem(file), media, modified, size));
        } catch (IOException | BundleException e) {
            LOG.log(Level.WARNING, "Ignoring the media of " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isCurrent(Path file, OpenBundle bundle) {
        try {
            return bundle.isCurrent(file);
        } catch (IOException _) {
            return false;
        }
    }

    private static void closeQuietly(OpenBundle bundle) {
        try {
            bundle.files().close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot close a bundle", e);
        }
    }

    @Override
    public synchronized void close() {
        bundles.values().forEach(MediaLibrary::closeQuietly);
        bundles.clear();
        bundleByMedia = Map.of();
    }
}
