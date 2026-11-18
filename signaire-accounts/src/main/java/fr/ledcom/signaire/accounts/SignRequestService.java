package fr.ledcom.signaire.accounts;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.ledcom.signaire.model.Language;

/** Users ask for signs that are missing; administrators add them, or reject the requests. */
@Service
@Transactional
public class SignRequestService {

    public static final int MAX_WORD_LENGTH = 100;
    public static final int MAX_COMMENT_LENGTH = 1000;

    private final SignRequestRepository requests;
    private final AccountRepository accounts;

    public SignRequestService(SignRequestRepository requests, AccountRepository accounts) {
        this.requests = requests;
        this.accounts = accounts;
    }

    /**
     * @param comment may be null or blank
     * @throws IllegalArgumentException if the word is blank or too long
     */
    public SignRequest request(long requesterId, String word, Language language, String comment) {
        String cleanWord = word == null ? "" : word.strip();
        if (cleanWord.isEmpty() || cleanWord.length() > MAX_WORD_LENGTH) {
            throw new IllegalArgumentException("Invalid word: " + word);
        }
        String cleanComment = comment == null || comment.isBlank() ? null : comment.strip();
        if (cleanComment != null && cleanComment.length() > MAX_COMMENT_LENGTH) {
            cleanComment = cleanComment.substring(0, MAX_COMMENT_LENGTH);
        }
        AccountEntity requester = accounts.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("No account " + requesterId));
        return requests.save(new SignRequestEntity(requester, cleanWord, language, cleanComment, now()))
                .toRequest();
    }

    /** The requests of a user, most recent first. */
    @Transactional(readOnly = true)
    public List<SignRequest> byRequester(long requesterId) {
        return requests.findByRequesterIdOrderByCreatedAtDescIdDesc(requesterId).stream()
                .map(SignRequestEntity::toRequest).toList();
    }

    /** The requests to handle, oldest first. */
    @Transactional(readOnly = true)
    public List<SignRequest> open() {
        return requests.findByStatusOrderByCreatedAtAscIdAsc(SignRequest.Status.OPEN).stream()
                .map(SignRequestEntity::toRequest).toList();
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return requests.countByStatus(SignRequest.Status.OPEN);
    }

    @Transactional(readOnly = true)
    public Optional<SignRequest> get(long id) {
        return requests.findById(id).map(SignRequestEntity::toRequest);
    }

    /** The sign was added, or already existed. */
    public void markAdded(long id, String signUid) {
        resolve(id, SignRequest.Status.ADDED, signUid);
    }

    public void reject(long id) {
        resolve(id, SignRequest.Status.REJECTED, null);
    }

    /** PostgreSQL keeps microseconds: the requests returned are the same as the requests read later. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void resolve(long id, SignRequest.Status status, String signUid) {
        requests.findById(id).ifPresent(request -> request.resolve(status, signUid, now()));
    }
}
