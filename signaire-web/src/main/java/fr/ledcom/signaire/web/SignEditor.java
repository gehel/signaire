package fr.ledcom.signaire.web;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.ledcom.signaire.accounts.SignOverride;
import fr.ledcom.signaire.accounts.SignOverrideService;
import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.model.Sign;

/**
 * Changes of administrators to signs: recorded in the database, and applied to the index right away and
 * again after each load of the bundles.
 */
@Service
public class SignEditor {

    private final SignOverrideService overrides;
    private final SignIndex index;
    private final BundleLoader loader;
    private final Path bundles;

    public SignEditor(SignOverrideService overrides, SignIndex index, BundleLoader loader,
            @Value("${signaire.bundles.directory:bundles}") Path bundles) {
        this.overrides = overrides;
        this.index = index;
        this.loader = loader;
        this.bundles = bundles;
    }

    /** Adds or replaces a sign. */
    public void save(Sign sign, long adminId) {
        overrides.save(sign, adminId);
        index.index(List.of(sign));
        index.refresh();
    }

    public void delete(String uid, long adminId) {
        overrides.delete(uid, adminId);
        index.delete(uid);
        index.refresh();
    }

    /** Forgets the change of a sign, and indexes the sign of the bundles again (if there is one). */
    public void revert(String uid) {
        overrides.revert(uid);
        loader.findInBundles(bundles, uid).ifPresentOrElse(
                sign -> index.index(List.of(sign)),
                () -> index.delete(uid));
        index.refresh();
    }

    /** Applies all the changes to the index, after loading bundles. */
    public void reapply() {
        List<SignOverride> all = overrides.all();
        index.index(all.stream().filter(override -> !override.deleted()).map(SignOverride::sign).toList());
        all.stream().filter(SignOverride::deleted).forEach(override -> index.delete(override.uid()));
        index.refresh();
    }
}
