package fr.ledcom.signaire.accounts;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The word lists of accounts. Every operation names the account doing it: a list of another account is
 * reported as not found.
 */
@Service
@Transactional
public class WordListService {

    public static final int MAX_NAME_LENGTH = 100;

    private final WordListRepository lists;
    private final AccountRepository accounts;

    public WordListService(WordListRepository lists, AccountRepository accounts) {
        this.lists = lists;
        this.accounts = accounts;
    }

    /** The lists of an account, by name. */
    @Transactional(readOnly = true)
    public List<WordList> lists(long ownerId) {
        return lists.findByOwnerId(ownerId).stream()
                .map(WordListEntity::toWordList)
                .sorted(Comparator.comparing(WordList::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public WordList get(long ownerId, long listId) {
        return find(ownerId, listId).toWordList();
    }

    /** @throws WordListException if the name is blank, too long, or already used by another list of the owner */
    public WordList create(long ownerId, String name) {
        String validName = validName(ownerId, name);
        AccountEntity owner = accounts.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("No account " + ownerId));
        return lists.save(new WordListEntity(owner, validName, Instant.now())).toWordList();
    }

    public WordList rename(long ownerId, long listId, String name) {
        WordListEntity list = find(ownerId, listId);
        String stripped = name == null ? "" : name.strip();
        // changing the case of the name only is fine, although the name is "taken"
        boolean sameName = list.toWordList().name().equalsIgnoreCase(stripped);
        list.rename(sameName ? stripped : validName(ownerId, name), Instant.now());
        return list.toWordList();
    }

    public void delete(long ownerId, long listId) {
        lists.delete(find(ownerId, listId));
    }

    /** Adds a sign to a list. Returns false when the sign was already in the list. */
    public boolean add(long ownerId, long listId, String signUid) {
        return find(ownerId, listId).add(signUid, Instant.now());
    }

    /** Removes a sign from a list. Returns false when the sign was not in the list. */
    public boolean remove(long ownerId, long listId, String signUid) {
        return find(ownerId, listId).remove(signUid, Instant.now());
    }

    /** Identifiers of the lists of an account that contain a sign. */
    @Transactional(readOnly = true)
    public List<Long> listsContaining(long ownerId, String signUid) {
        return lists.findIdsContaining(ownerId, signUid);
    }

    private WordListEntity find(long ownerId, long listId) {
        return lists.findByIdAndOwnerId(listId, ownerId).orElseThrow(() -> new WordListException(
                WordListException.Reason.NOT_FOUND, "No list " + listId + " for account " + ownerId));
    }

    private String validName(long ownerId, String name) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new WordListException(WordListException.Reason.INVALID_NAME, "Invalid list name: " + name);
        }
        if (lists.existsByOwnerIdAndNameIgnoreCase(ownerId, stripped)) {
            throw new WordListException(WordListException.Reason.NAME_TAKEN, "List name taken: " + stripped);
        }
        return stripped;
    }
}
