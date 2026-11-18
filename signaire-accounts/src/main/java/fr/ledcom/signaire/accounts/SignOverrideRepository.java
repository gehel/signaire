package fr.ledcom.signaire.accounts;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SignOverrideRepository extends JpaRepository<SignOverrideEntity, String> {

    List<SignOverrideEntity> findAllByOrderByUpdatedAtDesc();
}
