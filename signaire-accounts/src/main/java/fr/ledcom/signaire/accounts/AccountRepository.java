package fr.ledcom.signaire.accounts;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    Optional<AccountEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<AccountEntity> findByOidcIssuerAndOidcSubject(String issuer, String subject);
}
