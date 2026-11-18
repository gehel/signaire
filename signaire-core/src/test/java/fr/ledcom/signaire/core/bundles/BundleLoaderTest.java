package fr.ledcom.signaire.core.bundles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.core.FakeElasticsearch;
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.core.search.SignIndex;

class BundleLoaderTest {

    @TempDir
    Path bundles;

    @Test
    void errorsOfElasticsearchAreSearchExceptions() throws IOException {
        try (FakeElasticsearch elasticsearch = FakeElasticsearch.failing(true, 503, "cluster_block_exception")) {
            BundleLoader loader = new BundleLoader(new SignIndex(elasticsearch.client()), elasticsearch.client());

            assertThatThrownBy(loader::loadedBundles).isInstanceOf(SearchException.class);
        }
    }

    @Test
    void invalidBundlesHaveNoSigns() throws IOException {
        Files.writeString(bundles.resolve("broken.zip"), "not a zip");
        try (FakeElasticsearch elasticsearch = FakeElasticsearch.failing(true, 503, "cluster_block_exception")) {
            BundleLoader loader = new BundleLoader(new SignIndex(elasticsearch.client()), elasticsearch.client());

            assertThat(loader.findInBundles(bundles, "3")).isEmpty();
        }
    }
}
