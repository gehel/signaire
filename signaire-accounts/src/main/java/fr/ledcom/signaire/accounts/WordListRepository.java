package fr.ledcom.signaire.accounts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WordListRepository extends JpaRepository<WordListEntity, Long> {

    List<WordListEntity> findByOwnerId(long ownerId);

    Optional<WordListEntity> findByIdAndOwnerId(long id, long ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(long ownerId, String name);

    @Query("select e.wordList.id from WordListEntryEntity e "
            + "where e.wordList.owner.id = :owner and e.signUid = :uid order by e.wordList.id")
    List<Long> findIdsContaining(@Param("owner") long ownerId, @Param("uid") String signUid);
}
