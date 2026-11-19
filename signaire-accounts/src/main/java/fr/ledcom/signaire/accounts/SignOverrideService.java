package fr.ledcom.signaire.accounts;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.ledcom.signaire.model.Sign;
import tools.jackson.databind.json.JsonMapper;

/**
 * Signs added, edited or deleted by administrators. They are kept here because the index is rebuilt from the
 * bundles: applications apply them again after loading bundles.
 */
@Service
@Transactional
public class SignOverrideService {

    /** Uids of signs added by administrators, unlike the numeric uids of SignSuisse. */
    public static final String LOCAL_UID_PREFIX = "local-";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int UID_BYTES = 6;

    private final SignOverrideRepository overrides;
    private final AccountRepository accounts;
    private final SecureRandom random = new SecureRandom();

    public SignOverrideService(SignOverrideRepository overrides, AccountRepository accounts) {
        this.overrides = overrides;
        this.accounts = accounts;
    }

    /** All the changes, most recent first. */
    @Transactional(readOnly = true)
    public List<SignOverride> all() {
        return overrides.findAllByOrderByUpdatedAtDesc().stream().map(SignOverrideService::toOverride).toList();
    }

    @Transactional(readOnly = true)
    public Optional<SignOverride> find(String uid) {
        return overrides.findById(uid).map(SignOverrideService::toOverride);
    }

    /** Records a sign added or edited by an administrator. */
    public void save(Sign sign, long adminId) {
        update(sign.uid(), JSON.writeValueAsString(sign), adminId);
    }

    /** Records the deletion of a sign by an administrator. */
    public void delete(String uid, long adminId) {
        update(uid, null, adminId);
    }

    /** Forgets the change of a sign: the sign of the bundles applies again. */
    public void revert(String uid) {
        overrides.deleteById(uid);
    }

    public String newUid() {
        byte[] bytes = new byte[UID_BYTES];
        random.nextBytes(bytes);
        return LOCAL_UID_PREFIX + HexFormat.of().formatHex(bytes);
    }

    private void update(String uid, String json, long adminId) {
        SignOverrideEntity override = overrides.findById(uid).orElseGet(() -> new SignOverrideEntity(uid));
        override.update(json, accounts.findById(adminId).orElse(null), Instant.now());
        overrides.save(override);
    }

    private static SignOverride toOverride(SignOverrideEntity entity) {
        Sign sign = entity.signJson() == null ? null : JSON.readValue(entity.signJson(), Sign.class);
        return new SignOverride(entity.uid(), sign, entity.deleted(), entity.updatedAt(),
                entity.updatedByUsername());
    }
}
