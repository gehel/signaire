package fr.ledcom.signaire.accounts;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@SuppressFBWarnings(value = {"URF_UNREAD_FIELD", "FCBL_FIELD_COULD_BE_LOCAL"},
        justification = "JPA reads and writes the fields")
@Table(name = "account")
class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String displayName;
    private @Nullable String passwordHash;
    private @Nullable String oidcIssuer; // NOSONAR read by findByOidcIssuerAndOidcSubject
    private @Nullable String oidcSubject; // NOSONAR read by findByOidcIssuerAndOidcSubject
    private Instant createdAt; // NOSONAR a column, for administrators reading the database

    protected AccountEntity() {
        // for JPA
    }

    AccountEntity(String username, String displayName, Instant createdAt) {
        this.username = username;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    long id() {
        return id;
    }

    String username() {
        return username;
    }

    @Nullable String passwordHash() {
        return passwordHash;
    }

    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    void setOidcIdentity(String issuer, String subject) {
        this.oidcIssuer = issuer;
        this.oidcSubject = subject;
    }

    Account toAccount() {
        return new Account(id, username, displayName);
    }
}
