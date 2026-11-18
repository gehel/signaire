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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleManifest;
import fr.ledcom.signaire.bundle.BundleReader;
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.model.Sign;

/**
 * Loads bundles into the sign index. Loading is idempotent: a bundle whose signs did not change since it was
 * last loaded is skipped.
 */
@Service
public class BundleLoader {

    private static final String BUNDLE_EXTENSION = ".zip";
    /** One bundle per category, and a few more. */
    private static final int MAX_BUNDLES = 1000;

    private final SignIndex index;
    private final ElasticsearchClient client;
    private final BundleReader reader = new BundleReader();
    private final Clock clock;

    @Autowired
    public BundleLoader(SignIndex index, ElasticsearchClient client) {
        this(index, client, Clock.systemUTC());
    }

    BundleLoader(SignIndex index, ElasticsearchClient client, Clock clock) {
        this.index = index;
        this.client = client;
        this.clock = clock;
    }

    /** Loads all the bundles ({@code *.zip} files) of a directory. */
    public List<LoadReport> loadDirectory(Path directory) {
        return bundleFiles(directory).stream().map(this::load).toList();
    }

    /** The bundles loaded into the index, by file name. */
    public List<BundleRecord> loadedBundles() {
        index.createIfMissing();
        try {
            return client.search(s -> s.index(SignIndex.BUNDLES).size(MAX_BUNDLES), BundleRecord.class)
                    .hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(BundleRecord::file))
                    .toList();
        } catch (IOException | ElasticsearchException e) {
            throw new SearchException("Cannot read bundle registry", e);
        }
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
            } catch (BundleException e) {
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
        index.createIfMissing();
        try (Bundle bundle = reader.read(file)) {
            BundleManifest manifest = bundle.manifest();
            String key = Objects.toString(file.getFileName(), file.toString());
            String checksum = manifest.checksums().get(BundleManifest.SIGNS_ENTRY);
            BundleRecord previous = loaded(key);
            if (previous != null && previous.checksum().equals(checksum)) {
                return new LoadReport(file, manifest.category(), manifest.signCount(), LoadReport.Status.UNCHANGED,
                        null);
            }
            index.index(bundle.signs());
            register(key, new BundleRecord(key, manifest.category(), checksum, bundle.signs().size(),
                    clock.instant().toString()));
            index.refresh();
            return new LoadReport(file, manifest.category(), bundle.signs().size(), LoadReport.Status.LOADED, null);
        } catch (BundleException e) {
            return LoadReport.failed(file, e.getMessage());
        }
    }

    private BundleRecord loaded(String key) {
        try {
            GetResponse<BundleRecord> response = client.get(g -> g.index(SignIndex.BUNDLES).id(key),
                    BundleRecord.class);
            return response.found() ? response.source() : null;
        } catch (IOException | ElasticsearchException e) {
            throw new SearchException("Cannot read bundle registry", e);
        }
    }

    private void register(String key, BundleRecord bundle) {
        try {
            client.index(i -> i.index(SignIndex.BUNDLES).id(key).document(bundle));
        } catch (IOException | ElasticsearchException e) {
            throw new SearchException("Cannot update bundle registry", e);
        }
    }
}
