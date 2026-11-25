package fr.ledcom.signaire.accounts;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

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
    private @Nullable String signJson;
    private Instant updatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private @Nullable AccountEntity updatedBy;

    protected SignOverrideEntity() {
        // for JPA
    }

    SignOverrideEntity(String uid, @Nullable String json, @Nullable AccountEntity admin, Instant now) {
        this.uid = uid;
        this.deleted = json == null;
        this.signJson = json;
        this.updatedBy = admin;
        this.updatedAt = now;
    }

    void update(@Nullable String json, @Nullable AccountEntity admin, Instant now) {
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

    @Nullable String signJson() {
        return signJson;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    @Nullable String updatedByUsername() {
        return updatedBy == null ? null : updatedBy.username();
    }
}
