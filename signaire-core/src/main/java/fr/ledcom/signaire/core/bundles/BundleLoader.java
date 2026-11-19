package fr.ledcom.signaire.core.bundles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleManifest;
import fr.ledcom.signaire.bundle.BundleReader;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.model.Sign;

/**
 * Loads bundles into the sign index. Loading is idempotent: a bundle whose signs did not change since it was
 * last loaded is skipped.
 */
@Service
public class BundleLoader {

    private static final String BUNDLE_EXTENSION = ".zip";

    private final SignIndex index;
    private final BundleReader reader = new BundleReader();
    private final Clock clock;

    @Autowired
    public BundleLoader(SignIndex index) {
        this(index, Clock.systemUTC());
    }

    BundleLoader(SignIndex index, Clock clock) {
        this.index = index;
        this.clock = clock;
    }

    /** Loads all the bundles ({@code *.zip} files) of a directory. */
    public List<LoadReport> loadDirectory(Path directory) {
        return bundleFiles(directory).stream().map(this::load).toList();
    }

    /** The bundles loaded into the index, by file name. */
    public List<BundleRecord> loadedBundles() {
        return index.findBundles(BundleRecord.class).stream()
                .sorted(Comparator.comparing(BundleRecord::file))
                .toList();
    }

    /**
     * Finds a sign in the bundles of a directory, as it was extracted: to undo the changes of administrators.
     * Invalid bundles are skipped.
     */
    public Optional<Sign> findInBundles(Path directory, String uid) {
        for (Path file : bundleFiles(directory)) {
            try (Bundle bundle = reader.read(file)) {
                Optional<Sign> sign = bundle.signs().stream().filter(s -> s.uid().equals(uid)).findFirst();
                if (sign.isPresent()) {
                    return sign;
                }
            } catch (BundleException _) {
                // an invalid bundle has no sign to offer
            }
        }
        return Optional.empty();
    }

    private static List<Path> bundleFiles(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> String.valueOf(file.getFileName()).endsWith(BUNDLE_EXTENSION))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new BundleException("Cannot list bundles in " + directory, e);
        }
    }

    /** Loads a bundle, unless it was already loaded with the same signs. */
    public LoadReport load(Path file) {
        try (Bundle bundle = reader.read(file)) {
            BundleManifest manifest = bundle.manifest();
            String key = Objects.toString(file.getFileName(), file.toString());
            String checksum = manifest.checksums().get(BundleManifest.SIGNS_ENTRY);
            Optional<BundleRecord> previous = index.findBundle(key, BundleRecord.class);
            if (previous.isPresent() && previous.get().checksum().equals(checksum)) {
                return new LoadReport(file, manifest.category(), manifest.signCount(), LoadReport.Status.UNCHANGED,
                        null);
            }
            index.index(bundle.signs());
            index.saveBundle(key, new BundleRecord(key, manifest.category(), checksum, bundle.signs().size(),
                    clock.instant().toString()));
            index.refresh();
            return new LoadReport(file, manifest.category(), bundle.signs().size(), LoadReport.Status.LOADED, null);
        } catch (BundleException e) {
            return LoadReport.failed(file, e.getMessage());
        }
    }
}
