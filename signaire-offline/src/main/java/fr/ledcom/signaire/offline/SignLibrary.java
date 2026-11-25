package fr.ledcom.signaire.offline;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleFiles;
import fr.ledcom.signaire.bundle.BundleReader;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * The signs of the bundles of a directory, in memory, and their search: what the web application keeps in its index,
 * for a phone. {@link #load()} notices the bundles added, replaced or removed. The bundles stay open, for their media.
 */
@ThreadSafe
public final class SignLibrary implements AutoCloseable {

    private static final Logger LOG = System.getLogger(SignLibrary.class.getName());
    private static final String BUNDLE_EXTENSION = ".zip";

    private final Path directory;
    private final BundleReader reader = new BundleReader();
    /** By file: sorted, so that the bundles are listed by name. */
    @GuardedBy("this")
    private final Map<Path, Loaded> bundles = new TreeMap<>();
    /** By name. */
    @GuardedBy("this")
    private List<Entry> entries = List.of();
    @GuardedBy("this")
    private Map<String, Sign> byUid = Map.of();
    /** The bundle holding each media. */
    @GuardedBy("this")
    private Map<String, Bundle> mediaBundles = Map.of();

    /** A bundle, and the version of its file. */
    private record Loaded(Bundle bundle, FileTime modified, long size) {
        BundleInfo info() {
            return new BundleInfo(bundle.file(), bundle.manifest().category(), bundle.signs().size(),
                    bundle.manifest().createdAt());
        }
    }

    public SignLibrary(Path directory) {
        this.directory = directory;
    }

    /** Where the bundles are. */
    public Path directory() {
        return directory;
    }

    /** Takes into account the bundles added, replaced or removed since the last time; skips invalid ones. */
    public synchronized List<BundleInfo> load() {
        List<Path> files = bundleFiles();
        Iterator<Map.Entry<Path, Loaded>> loaded = bundles.entrySet().iterator();
        while (loaded.hasNext()) {
            Map.Entry<Path, Loaded> bundle = loaded.next();
            if (!files.contains(bundle.getKey()) || !isCurrent(bundle.getKey(), bundle.getValue())) {
                bundle.getValue().bundle().close();
                loaded.remove();
            }
        }
        for (Path file : files) {
            if (!bundles.containsKey(file)) {
                open(file);
            }
        }
        index();
        return bundles();
    }

    public synchronized List<BundleInfo> bundles() {
        return bundles.values().stream().map(Loaded::info).toList();
    }

    public synchronized Optional<Sign> sign(String uid) {
        return Optional.ofNullable(byUid.get(uid));
    }

    /** The signs of these uids, in their order; those not in the library are left out. */
    public synchronized List<Sign> signs(Collection<String> uids) {
        List<Sign> signs = new ArrayList<>();
        for (String uid : uids) {
            Sign sign = byUid.get(uid);
            if (sign != null) {
                signs.add(sign);
            }
        }
        return signs;
    }

    /** Signs matching the words of the query (best first), or all of them by name without words. */
    public synchronized List<Sign> search(Query query) {
        Stream<Entry> filtered = entries.stream()
                .filter(entry -> query.languages().isEmpty() || query.languages().contains(entry.sign().language()))
                .filter(entry -> query.categories().isEmpty()
                        || entry.sign().categories().stream().anyMatch(query.categories()::contains));
        String text = query.text();
        if (text == null) {
            return filtered.map(Entry::sign).toList();
        }
        String folded = Folding.fold(text);
        List<String> words = Folding.words(folded);
        record Ranked(Entry entry, int rank) {
        }
        return filtered.map(entry -> new Ranked(entry, entry.rank(folded, words)))
                .filter(ranked -> ranked.rank() != Entry.NO_MATCH)
                // stable: by name within a rank
                .sorted(Comparator.comparingInt(Ranked::rank))
                .map(ranked -> ranked.entry().sign())
                .toList();
    }

    /**
     * Imports a bundle: checked, stored under the name of its category, loaded.
     *
     * @throws BundleException if the content is not a valid bundle
     */
    public BundleInfo importBundle(InputStream content) throws IOException {
        Path file = BundleFiles.store(content, directory);
        load();
        synchronized (this) {
            Loaded imported = bundles.get(file);
            if (imported == null) {
                throw new BundleException("Cannot load the imported bundle " + file);
            }
            return imported.info();
        }
    }

    /**
     * The file of a media downloaded in a bundle, copied out of it into a cache directory (players need a file);
     * empty when the media was not downloaded, so that it plays from SignSuisse.
     */
    public Optional<Path> media(Media media, Path cache) throws IOException {
        String name = media.file();
        if (name == null) {
            return Optional.empty();
        }
        Path target = cache.resolve(name).normalize();
        if (!target.startsWith(cache.normalize())) {
            throw new IOException("Media outside the cache: " + name);
        }
        if (Files.isRegularFile(target)) {
            return Optional.of(target);
        }
        Bundle bundle;
        synchronized (this) {
            bundle = mediaBundles.get(name);
        }
        if (bundle == null) {
            return Optional.empty();
        }
        Path parent = target.getParent();
        Files.createDirectories(parent == null ? cache : parent);
        Path partial = Files.createTempFile(parent == null ? cache : parent, ".media-", ".tmp");
        try {
            try (InputStream in = bundle.openMedia(name)) {
                Files.copy(in, partial, REPLACE_EXISTING);
            }
            Files.move(partial, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial);
        }
        return Optional.of(target);
    }

    @Override
    public synchronized void close() {
        bundles.values().forEach(loaded -> loaded.bundle().close());
        bundles.clear();
        index();
    }

    private List<Path> bundleFiles() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> String.valueOf(file.getFileName()).endsWith(BUNDLE_EXTENSION)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the bundles of " + directory, e);
        }
    }

    @GuardedBy("this")
    private void open(Path file) {
        try {
            FileTime modified = Files.getLastModifiedTime(file);
            long size = Files.size(file);
            bundles.put(file, new Loaded(reader.read(file), modified, size));
        } catch (BundleException | IOException e) {
            LOG.log(Level.WARNING, "Ignoring the invalid bundle {0}: {1}", file, e.getMessage());
        }
    }

    private static boolean isCurrent(Path file, Loaded loaded) {
        try {
            return loaded.modified().equals(Files.getLastModifiedTime(file)) && loaded.size() == Files.size(file);
        } catch (IOException e) {
            return false;
        }
    }

    /** The entries of the signs of every bundle, by name; a sign in several bundles (categories) once. */
    @GuardedBy("this")
    private void index() {
        Map<String, Sign> signs = new LinkedHashMap<>();
        Map<String, Bundle> media = new HashMap<>();
        for (Loaded loaded : bundles.values()) {
            for (Sign sign : loaded.bundle().signs()) {
                signs.putIfAbsent(sign.uid(), sign);
            }
            loaded.bundle().manifest().checksums().keySet().forEach(name -> media.putIfAbsent(name, loaded.bundle()));
        }
        entries = signs.values().stream().map(Entry::of)
                .sorted(Comparator.comparing(Entry::name).thenComparing(entry -> entry.sign().uid()))
                .toList();
        byUid = Map.copyOf(signs);
        mediaBundles = Map.copyOf(media);
    }
}
