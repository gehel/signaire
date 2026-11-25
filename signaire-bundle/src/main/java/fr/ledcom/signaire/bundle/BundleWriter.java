package fr.ledcom.signaire.bundle;

import static fr.ledcom.signaire.bundle.BundleManifest.CURRENT_FORMAT_VERSION;
import static fr.ledcom.signaire.bundle.BundleManifest.MANIFEST_ENTRY;
import static fr.ledcom.signaire.bundle.BundleManifest.SIGNS_ENTRY;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collection;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jspecify.annotations.Nullable;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/** Writes the signs of a category, and their downloaded media, into a bundle. */
public class BundleWriter {

    private final Clock clock;

    public BundleWriter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Writes a bundle.
     *
     * <p>The bundle is first written to a temporary file next to {@code target}, then moved into place, so
     * that a bundle directory never contains a partially written bundle.
     *
     * @param target the bundle file to write; replaced if it exists
     * @param category the category of the signs
     * @param signs the signs to store
     * @param mediaRoot directory against which the {@link Media#file()} of the signs are resolved
     * @return {@code target}
     */
    public Path write(Path target, Category category, List<Sign> signs, Path mediaRoot) {
        Map<String, Path> mediaFiles = resolveMediaFiles(signs, mediaRoot);
        Path absoluteTarget = target.toAbsolutePath();
        Path directory = Objects.requireNonNull(absoluteTarget.getParent(), "target directory");
        @Nullable Path tmp = null;
        try {
            // not Files.createTempFile, whose files are only readable by their owner: bundles get the usual
            // permissions (umask), as they are meant to be read by other processes
            tmp = directory.resolve(".bundle-" + UUID.randomUUID() + ".tmp");
            writeZip(tmp, category, signs, mediaFiles);
            Files.move(tmp, absoluteTarget, ATOMIC_MOVE, REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new BundleException("Cannot write bundle " + target, e);
        } finally {
            deleteQuietly(tmp);
        }
    }

    private void writeZip(Path zip, Category category, List<Sign> signs, Map<String, Path> mediaFiles)
            throws IOException {
        Map<String, String> checksums = new LinkedHashMap<>();
        try (OutputStream out = Files.newOutputStream(zip, CREATE_NEW, WRITE);
                ZipOutputStream zipOut = new ZipOutputStream(out)) {
            byte[] signsJson = BundleJson.MAPPER.writeValueAsBytes(signs);
            putEntry(zipOut, SIGNS_ENTRY, signsJson);
            checksums.put(SIGNS_ENTRY, Checksums.sha256(signsJson));

            // videos and images are already compressed
            zipOut.setLevel(Deflater.NO_COMPRESSION);
            for (Map.Entry<String, Path> media : mediaFiles.entrySet()) {
                zipOut.putNextEntry(new ZipEntry(media.getKey()));
                try (InputStream in = Files.newInputStream(media.getValue())) {
                    in.transferTo(zipOut);
                }
                zipOut.closeEntry();
                try (InputStream in = Files.newInputStream(media.getValue())) {
                    checksums.put(media.getKey(), Checksums.sha256(in));
                }
            }

            zipOut.setLevel(Deflater.DEFAULT_COMPRESSION);
            BundleManifest manifest = new BundleManifest(
                    CURRENT_FORMAT_VERSION, category, clock.instant(), signs.size(), checksums);
            putEntry(zipOut, MANIFEST_ENTRY, BundleJson.MAPPER.writeValueAsBytes(manifest));
        }
    }

    private static void putEntry(ZipOutputStream zipOut, String name, byte[] content) throws IOException {
        zipOut.putNextEntry(new ZipEntry(name));
        zipOut.write(content);
        zipOut.closeEntry();
    }

    /** Media files of the signs, by entry name, checked to exist inside {@code mediaRoot}. */
    private static Map<String, Path> resolveMediaFiles(Collection<Sign> signs, Path mediaRoot) {
        Path root = mediaRoot.toAbsolutePath().normalize();
        Set<String> names = new LinkedHashSet<>();
        signs.stream().flatMap(BundleWriter::media).map(Media::file).filter(Objects::nonNull).forEach(names::add);

        Map<String, Path> files = new LinkedHashMap<>();
        for (String name : names) {
            Path file = root.resolve(name).normalize();
            if (!file.startsWith(root) || BundleEntries.isReserved(name)) {
                throw new BundleException("Media file " + name + " is not inside " + mediaRoot);
            }
            if (!Files.isRegularFile(file)) {
                throw new BundleException("Media file " + name + " not found in " + mediaRoot);
            }
            files.put(name, file);
        }
        return files;
    }

    private static Stream<Media> media(Sign sign) {
        return Stream.of(
                sign.videos().stream(),
                sign.examples().stream().map(Example::video).filter(Objects::nonNull),
                sign.images().stream(),
                sign.dialectVariants().stream().map(DialectVariant::video))
            .flatMap(Function.identity());
    }

    private static void deleteQuietly(@Nullable Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // best effort cleanup of the temporary file, the original error matters more
        }
    }
}
