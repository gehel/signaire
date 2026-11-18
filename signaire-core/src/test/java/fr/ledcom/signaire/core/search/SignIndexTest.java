package fr.ledcom.signaire.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Sign;

class SignIndexTest {

    @TempDir
    Path directory;

    /** A record of the bundle registry. */
    record Registered(String file, Category category) {
    }

    @Test
    void createsTheIndexOnFirstUse() throws IOException {
        Path missing = directory.resolve("state/index");
        try (SignIndex index = new SignIndex(missing)) {
            index.createIfMissing();

            assertThat(new SignSearchService(index).search(SignQuery.all(0, 10)).total()).isZero();
        }
        assertThat(missing).isDirectory();
    }

    @Test
    void keepsTheSignsBetweenRuns() throws IOException {
        try (SignIndex index = new SignIndex(directory)) {
            index.index(List.of(TestSigns.CHAT));
        }

        try (SignIndex index = new SignIndex(directory)) {
            assertThat(new SignSearchService(index).findByUid(TestSigns.CHAT.uid())).contains(TestSigns.CHAT);
        }
    }

    @Test
    void emptiesAnIndexOfAnotherSchema() throws IOException {
        try (SignIndex index = new SignIndex(directory)) {
            index.index(List.of(TestSigns.CHAT));
        }
        try (Directory lucene = FSDirectory.open(directory);
                IndexWriter writer = new IndexWriter(lucene, new IndexWriterConfig())) {
            writer.setLiveCommitData(Map.of("signaire.schema", "0").entrySet());
            writer.commit();
        }

        try (SignIndex index = new SignIndex(directory)) {
            assertThat(new SignSearchService(index).findByUid(TestSigns.CHAT.uid())).isEmpty();
        }
    }

    @Test
    void indexesShareTheirDirectory() throws IOException {
        // e.g. the command line loads bundles while the web application runs
        try (SignIndex web = new SignIndex(directory); SignIndex cli = new SignIndex(directory)) {
            SignSearchService search = new SignSearchService(web);
            web.createIfMissing();

            cli.index(List.of(TestSigns.GATTO));

            assertThat(search.findByUid(TestSigns.GATTO.uid())).contains(TestSigns.GATTO);
        }
    }

    @Test
    void waitsForTheWritesOfAnotherProcess() throws IOException {
        try (SignIndex index = new SignIndex(directory, Duration.ofMillis(200))) {
            index.createIfMissing();
            List<Sign> signs = List.of(TestSigns.CHAT);
            try (Directory lucene = FSDirectory.open(directory);
                    IndexWriter other = new IndexWriter(lucene, new IndexWriterConfig())) {

                assertThat(other.isOpen()).isTrue();
                assertThatThrownBy(() -> index.index(signs)).isInstanceOf(SearchException.class)
                        .hasMessageContaining("another process");
            }

            index.index(signs);
            assertThat(new SignSearchService(index).findByUid(TestSigns.CHAT.uid())).isPresent();
        }
    }

    @Test
    void recordsBundles() throws IOException {
        try (SignIndex index = new SignIndex(directory)) {
            index.saveBundle("city.zip", new Registered("city.zip", Category.CITY));
            index.saveBundle("animals.zip", new Registered("animals.zip", Category.ANIMALS));
            index.saveBundle("city.zip", new Registered("city.zip", Category.CITY));

            assertThat(index.findBundle("city.zip", Registered.class))
                    .contains(new Registered("city.zip", Category.CITY));
            assertThat(index.findBundle("other.zip", Registered.class)).isEmpty();
            assertThat(index.findBundles(Registered.class)).hasSize(2);
        }
    }

    @Test
    void anUnusableDirectoryIsASearchError() throws IOException {
        Path file = Files.writeString(directory.resolve("index"), "not a directory");
        try (SignIndex index = new SignIndex(file)) {
            List<Sign> signs = List.of(TestSigns.CHAT);

            assertThatThrownBy(index::createIfMissing).isInstanceOf(SearchException.class)
                    .hasMessageContaining(file.toString());
            assertThatThrownBy(() -> index.index(signs)).isInstanceOf(SearchException.class);
            assertThatThrownBy(index::refresh).isInstanceOf(SearchException.class);
        }
    }

    @Test
    void unreadableRecordsAreSearchErrors() throws IOException {
        try (SignIndex index = new SignIndex(directory)) {
            index.saveBundle("city.zip", "not a record");

            assertThatThrownBy(() -> index.findBundles(Registered.class)).isInstanceOf(SearchException.class);
        }
    }

    @Test
    void closingTwiceIsFine() throws IOException {
        SignIndex index = new SignIndex(directory);
        index.createIfMissing();
        index.close();
        index.close();

        assertThat(new SignSearchService(index).search(SignQuery.all(0, 1)).total()).isZero();
        index.close();
    }
}
