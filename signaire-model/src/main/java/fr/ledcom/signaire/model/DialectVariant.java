package fr.ledcom.signaire.model;

import java.util.List;
import java.util.Objects;

/**
 * A regional variant of a sign.
 *
 * @param dialects the regions using this variant, as SignSuisse abbreviates them (e.g. "BE", "SG")
 * @param video the variant signed
 */
public record DialectVariant(List<String> dialects, Media video) {

    public DialectVariant {
        Objects.requireNonNull(video, "video");
        dialects = dialects == null ? List.of() : List.copyOf(dialects);
    }
}
