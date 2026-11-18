package fr.ledcom.signaire.core.bundles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleManifest;
import fr.ledcom.signaire.bundle.BundleReader;
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.core.search.SignIndex;

/**
 * Loads bundles into the sign index. Loading is idempotent: a bundle whose signs did not change since it was
 * last loaded is skipped.
 */
@Service
public class BundleLoader {

    private static final String BUNDLE_EXTENSION = ".zip";

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
        List<Path> bundles;
        try (Stream<Path> files = Files.list(directory)) {
            bundles = files.filter(file -> String.valueOf(file.getFileName()).endsWith(BUNDLE_EXTENSION))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new BundleException("Cannot list bundles in " + directory, e);
        }
        return bundles.stream().map(this::load).toList();
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
        } catch (IOException e) {
            throw new SearchException("Cannot read bundle registry", e);
        }
    }

    private void register(String key, BundleRecord bundle) {
        try {
            client.index(i -> i.index(SignIndex.BUNDLES).id(key).document(bundle));
        } catch (IOException e) {
            throw new SearchException("Cannot update bundle registry", e);
        }
    }
}
