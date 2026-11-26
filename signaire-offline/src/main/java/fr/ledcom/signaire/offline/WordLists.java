package fr.ledcom.signaire.offline;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The word lists of the phone, in a JSON file, read once and written whole after each change: a few lists of a few
 * hundred signs. Names follow the rules of the web application's lists.
 */
@ThreadSafe
public final class WordLists {

    public static final int MAX_NAME_LENGTH = 100;

    /** Records written from their fields, as in bundles: Jackson doesn't see the accessors of records on Android. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withVisibility(PropertyAccessor.FIELD, Visibility.ANY))
            .build();
    private static final TypeReference<List<WordList>> LIST_TYPE = new TypeReference<>() { };

    private final Path file;
    @GuardedBy("this")
    private List<WordList> lists;

    public WordLists(Path file) {
        this.file = file;
        this.lists = read(file);
    }

    /** By name. */
    public synchronized List<WordList> lists() {
        return lists.stream().sorted(Comparator.comparing(list -> list.name().toLowerCase(Locale.ROOT))).toList();
    }

    public synchronized Optional<WordList> list(long id) {
        return lists.stream().filter(list -> list.id() == id).findFirst();
    }

    /** The lists holding a sign, by name. */
    public synchronized List<WordList> listsWith(String uid) {
        return lists().stream().filter(list -> list.signs().contains(uid)).toList();
    }

    /** @throws WordListException if the name is blank, too long, or already the name of a list */
    public synchronized WordList create(String name) {
        long id = lists.stream().mapToLong(WordList::id).max().orElse(0) + 1;
        WordList created = new WordList(id, validName(name, null), List.of());
        List<WordList> changed = new ArrayList<>(lists);
        changed.add(created);
        save(changed);
        return created;
    }

    /** @throws WordListException if the list doesn't exist, or the name is not valid */
    public synchronized WordList rename(long id, String name) {
        WordList list = existing(id);
        return replace(new WordList(id, validName(name, id), list.signs()));
    }

    public synchronized void delete(long id) {
        existing(id);
        save(lists.stream().filter(list -> list.id() != id).toList());
    }

    /** Adds a sign at the end of a list, unless it is already there. */
    public synchronized WordList add(long id, String uid) {
        WordList list = existing(id);
        if (list.signs().contains(uid)) {
            return list;
        }
        List<String> signs = new ArrayList<>(list.signs());
        signs.add(uid);
        return replace(new WordList(id, list.name(), signs));
    }

    public synchronized WordList remove(long id, String uid) {
        WordList list = existing(id);
        return replace(new WordList(id, list.name(), list.signs().stream().filter(sign -> !sign.equals(uid)).toList()));
    }

    @GuardedBy("this")
    private WordList existing(long id) {
        return list(id).orElseThrow(() -> new WordListException(WordListException.Reason.NOT_FOUND, "No list " + id));
    }

    @GuardedBy("this")
    private String validName(String name, @Nullable Long renamed) {
        String stripped = name.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new WordListException(WordListException.Reason.INVALID_NAME, "Invalid list name: " + name);
        }
        boolean taken = lists.stream().anyMatch(list -> list.name().equalsIgnoreCase(stripped)
                && (renamed == null || list.id() != renamed));
        if (taken) {
            throw new WordListException(WordListException.Reason.NAME_TAKEN, "List name taken: " + stripped);
        }
        return stripped;
    }

    @GuardedBy("this")
    private WordList replace(WordList changed) {
        save(lists.stream().map(list -> list.id() == changed.id() ? changed : list).toList());
        return changed;
    }

    /** Writes the lists to a temporary file, then moves it: the file is never half written. */
    @GuardedBy("this")
    private void save(List<WordList> changed) {
        Path directory = Objects.requireNonNull(file.toAbsolutePath().getParent(), "directory of the lists");
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, ".lists-", ".tmp");
            try {
                JSON.writeValue(temporary.toFile(), changed);
                Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the word lists to " + file, e);
        }
        lists = List.copyOf(changed);
    }

    private static List<WordList> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return List.copyOf(JSON.readValue(file.toFile(), LIST_TYPE));
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot read the word lists of " + file, e);
        }
    }
}
