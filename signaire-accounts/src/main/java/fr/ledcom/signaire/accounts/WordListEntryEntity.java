package fr.ledcom.signaire.accounts;

import java.time.Instant;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Table {@code word_list_entry}: a sign of a word list. */
@Entity
@SuppressFBWarnings(value = {"URF_UNREAD_FIELD", "FCBL_FIELD_COULD_BE_LOCAL"},
        justification = "JPA reads and writes the fields")
@Table(name = "word_list_entry")
class WordListEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_list_id")
    private WordListEntity wordList;
    private String signUid;
    private Instant addedAt; // NOSONAR read by the @OrderBy of WordListEntity.entries

    protected WordListEntryEntity() {
        // for JPA
    }

    WordListEntryEntity(WordListEntity wordList, String signUid, Instant addedAt) { // NOSONAR id: set by JPA
        this.wordList = wordList;
        this.signUid = signUid;
        this.addedAt = addedAt;
    }

    String signUid() {
        return signUid;
    }
}
