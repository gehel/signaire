package fr.ledcom.signaire.core.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.ledcom.signaire.model.Sign;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * The Lucene index of Signaire, in a directory: the signs, and the registry of the loaded bundles. It can be
 * rebuilt from the bundles at any time, and it is when its {@link #SCHEMA} changes.
 *
 * <p>Several processes can share the index (the web application and the command line): writes are short, each
 * one commits, and waits while another process writes. Searches see the commits of all processes.
 */
@Service
@ThreadSafe
public class SignIndex implements AutoCloseable {

    /**
     * Version of the fields and their analysis ({@link SignDocuments}, {@link SignAnalyzers}): an index of another
     * version is emptied when it is opened, and the bundles are loaded again.
     */
    static final String SCHEMA = "1";

    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(30);
    /** One bundle per category, and a few more. */
    private static final int MAX_BUNDLES = 1000;

    private final Path path;
    private final IndexWriters writers;
    /** Lucene allows one writer at a time, in this process too. */
    private final ReentrantLock writing = new ReentrantLock();
    @GuardedBy("this")
    private Directory directory;
    @GuardedBy("this")
    private SearcherManager searchers;

    /** A step of a write. */
    @FunctionalInterface
    private interface Write {
        void apply(IndexWriter writer) throws IOException;
    }

    @FunctionalInterface
    interface Read<T> {
        T apply(IndexSearcher searcher) throws IOException;
    }

    @Autowired
    public SignIndex(@Value("${signaire.index.directory}") Path path) {
        this(path, DEFAULT_LOCK_TIMEOUT);
    }

    /**
     * @param lockTimeout how long to wait for the writes of other processes
     */
    public SignIndex(Path path, Duration lockTimeout) {
        this.path = path;
        this.writers = new IndexWriters(path, SCHEMA, lockTimeout);
    }

    /** Opens the index, creating it when it doesn't exist or has another schema. */
    public void createIfMissing() {
        searchers();
    }

    /** Empties the index: signs and bundles. */
    public void recreate() {
        write("Cannot empty index", IndexWriter::deleteAll);
    }

    /** Adds or replaces signs. */
    public void index(Collection<Sign> signs) {
        List<Sign> all = List.copyOf(signs);
        write("Cannot index signs", writer -> {
            for (Sign sign : all) {
                writer.updateDocument(SignDocuments.signId(sign.uid()), SignDocuments.signDocument(sign));
            }
        });
    }

    /** Removes a sign; nothing happens when it is not in the index. */
    public void delete(String uid) {
        write("Cannot delete sign " + uid, writer -> writer.deleteDocuments(SignDocuments.signId(uid)));
    }

    /** Makes the latest changes visible to searches, those of other processes too. */
    public void refresh() {
        try {
            searchers().maybeRefreshBlocking();
        } catch (IOException e) {
            throw new SearchException("Cannot refresh index " + path, e);
        }
    }

    /** Records a loaded bundle, replacing the previous record of the same file. */
    public void saveBundle(String file, Object entry) {
        write("Cannot record bundle " + file,
                writer -> writer.updateDocument(SignDocuments.bundleId(file), SignDocuments.bundleDocument(file, entry)));
    }

    /** The record of a loaded bundle. */
    public <T> Optional<T> findBundle(String file, Class<T> type) {
        return read("Cannot read bundle registry", searcher -> {
            TopDocs top = searcher.search(new TermQuery(SignDocuments.bundleId(file)), 1);
            return top.scoreDocs.length == 0 ? Optional.<T>empty()
                    : Optional.of(SignDocuments.readSource(searcher.storedFields(), top.scoreDocs[0].doc, type));
        });
    }

    /** The records of all the loaded bundles. */
    public <T> List<T> findBundles(Class<T> type) {
        return read("Cannot read bundle registry", searcher -> {
            StoredFields fields = searcher.storedFields();
            List<T> bundles = new ArrayList<>();
            TermQuery all = new TermQuery(new Term(SignDocuments.KIND, SignDocuments.BUNDLE));
            for (ScoreDoc doc : searcher.search(all, MAX_BUNDLES).scoreDocs) {
                bundles.add(SignDocuments.readSource(fields, doc.doc, type));
            }
            return bundles;
        });
    }

    @Override
    public synchronized void close() throws IOException {
        if (searchers != null) {
            searchers.close();
            searchers = null;
        }
        if (directory != null) {
            directory.close();
            directory = null;
        }
    }

    /** Reads the index, with the latest commits. */
    <T> T read(String error, Read<T> read) {
        SearcherManager manager = searchers();
        try {
            manager.maybeRefresh();
            IndexSearcher searcher = manager.acquire();
            try {
                return read.apply(searcher);
            } finally {
                manager.release(searcher);
            }
        } catch (IOException e) {
            throw new SearchException(error, e);
        }
    }

    private void write(String error, Write write) {
        Directory opened = directory();
        writing.lock();
        try (IndexWriter writer = writers.open(opened)) {
            write.apply(writer);
            writers.commit(writer);
        } catch (IOException e) {
            throw new SearchException(error + " in " + path, e);
        } finally {
            writing.unlock();
        }
    }

    private synchronized Directory directory() {
        if (directory == null) {
            try {
                Files.createDirectories(path);
                directory = FSDirectory.open(path);
            } catch (IOException e) {
                throw new SearchException("Cannot open index " + path, e);
            }
        }
        return directory;
    }

    private synchronized SearcherManager searchers() {
        if (searchers == null) {
            Directory opened = directory();
            try {
                if (!writers.hasCurrentSchema(opened)) {
                    write("Cannot create index", IndexWriter::deleteAll);
                }
                searchers = new SearcherManager(opened, null);
            } catch (IOException e) {
                throw new SearchException("Cannot open index " + path, e);
            }
        }
        return searchers;
    }
}
