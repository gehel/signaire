package fr.ledcom.signaire.core.search;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;

/**
 * Opens the writers of an index, one at a time across processes, and commits with the version of the schema.
 */
final class IndexWriters {

    private static final String SCHEMA_KEY = "signaire.schema";
    private static final Duration RETRY = Duration.ofMillis(50);

    private final Path path;
    private final String schema;
    private final Duration lockTimeout;

    /**
     * @param path the directory of the index, for messages
     * @param lockTimeout how long to wait for the writes of other processes
     */
    IndexWriters(Path path, String schema, Duration lockTimeout) {
        this.path = path;
        this.schema = schema;
        this.lockTimeout = lockTimeout;
    }

    /** A writer, once other processes are done writing. Changes that are not committed are lost. */
    IndexWriter open(Directory directory) throws IOException {
        Instant deadline = Instant.now().plus(lockTimeout);
        while (true) {
            try {
                // a configuration belongs to one writer, even one that could not be created
                return new IndexWriter(directory, new IndexWriterConfig(SignAnalyzers.indexing())
                        .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
                        .setCommitOnClose(false));
            } catch (LockObtainFailedException e) {
                if (Instant.now().isAfter(deadline)) {
                    throw new SearchException("Index " + path + " is still written by another process", e);
                }
                pause();
            }
        }
    }

    /** Commits the changes, with the version of the schema. */
    void commit(IndexWriter writer) throws IOException {
        writer.setLiveCommitData(Map.of(SCHEMA_KEY, schema).entrySet());
        writer.commit();
    }

    /** Whether the directory holds an index of the current schema. */
    boolean hasCurrentSchema(Directory directory) throws IOException {
        return DirectoryReader.indexExists(directory)
                && schema.equals(SegmentInfos.readLatestCommit(directory).getUserData().get(SCHEMA_KEY));
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchException("Interrupted while waiting for the index", e);
        }
    }
}
