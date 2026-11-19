package fr.ledcom.signaire.accounts;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sign_override")
class SignOverrideEntity {

    @Id
    private String uid;
    private boolean deleted;
    private String signJson;
    private Instant updatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AccountEntity updatedBy;

    protected SignOverrideEntity() {
        // for JPA
    }

    SignOverrideEntity(String uid) {
        this.uid = uid;
    }

    void update(String json, AccountEntity admin, Instant now) {
        this.deleted = json == null;
        this.signJson = json;
        this.updatedBy = admin;
        this.updatedAt = now;
    }

    String uid() {
        return uid;
    }

    boolean deleted() {
        return deleted;
    }

    String signJson() {
        return signJson;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    String updatedByUsername() {
        return updatedBy == null ? null : updatedBy.username();
    }
}
