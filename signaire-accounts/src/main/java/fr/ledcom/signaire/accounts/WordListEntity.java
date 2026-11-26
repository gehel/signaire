package fr.ledcom.signaire.accounts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Table {@code word_list}, with its entries. */
@Entity
@SuppressFBWarnings(value = {"URF_UNREAD_FIELD", "FCBL_FIELD_COULD_BE_LOCAL"},
        justification = "JPA reads and writes the fields")
@Table(name = "word_list")
class WordListEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private AccountEntity owner;
    private String name;
    private Instant createdAt; // NOSONAR a column, for administrators reading the database
    private Instant updatedAt; // NOSONAR a column, for administrators reading the database
    @OneToMany(mappedBy = "wordList", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt, id")
    private List<WordListEntryEntity> entries = new ArrayList<>();

    protected WordListEntity() {
        // for JPA
    }

    WordListEntity(AccountEntity owner, String name, Instant createdAt) { // NOSONAR id: set by JPA
        this.owner = owner;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    void rename(String newName, Instant now) {
        name = newName;
        updatedAt = now;
    }

    /** Adds a sign, unless it's already in the list. */
    boolean add(String signUid, Instant now) {
        if (entries.stream().anyMatch(entry -> entry.signUid().equals(signUid))) {
            return false;
        }
        entries.add(new WordListEntryEntity(this, signUid, now));
        updatedAt = now;
        return true;
    }

    boolean remove(String signUid, Instant now) {
        boolean removed = entries.removeIf(entry -> entry.signUid().equals(signUid));
        if (removed) {
            updatedAt = now;
        }
        return removed;
    }

    WordList toWordList() {
        return new WordList(id, name, entries.stream().map(WordListEntryEntity::signUid).toList());
    }
}
