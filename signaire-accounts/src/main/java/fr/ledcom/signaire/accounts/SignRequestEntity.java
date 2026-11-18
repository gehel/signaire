package fr.ledcom.signaire.accounts;

import java.time.Instant;

import fr.ledcom.signaire.model.Language;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Table {@code sign_request}. */
@Entity
@Table(name = "sign_request")
class SignRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private AccountEntity requester;
    private String word;
    @Enumerated(EnumType.STRING)
    private Language language;
    private String comment;
    @Enumerated(EnumType.STRING)
    private SignRequest.Status status;
    private String signUid;
    private Instant createdAt;
    private Instant resolvedAt;

    protected SignRequestEntity() {
        // for JPA
    }

    SignRequestEntity(AccountEntity requester, String word, Language language, String comment, Instant now) {
        this.requester = requester;
        this.word = word;
        this.language = language;
        this.comment = comment;
        this.status = SignRequest.Status.OPEN;
        this.createdAt = now;
    }

    void resolve(SignRequest.Status newStatus, String uid, Instant now) {
        this.status = newStatus;
        this.signUid = uid;
        this.resolvedAt = now;
    }

    SignRequest toRequest() {
        return new SignRequest(id, requester.username(), word, language, comment, status, signUid, createdAt,
                resolvedAt);
    }
}
