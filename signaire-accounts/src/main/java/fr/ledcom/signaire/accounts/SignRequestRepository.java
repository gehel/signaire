package fr.ledcom.signaire.accounts;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SignRequestRepository extends JpaRepository<SignRequestEntity, Long> {

    List<SignRequestEntity> findByRequesterIdOrderByCreatedAtDescIdDesc(long requesterId);

    List<SignRequestEntity> findByStatusOrderByCreatedAtAscIdAsc(SignRequest.Status status);

    long countByStatus(SignRequest.Status status);
}
