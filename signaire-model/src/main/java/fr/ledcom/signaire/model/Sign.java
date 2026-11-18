package fr.ledcom.signaire.model;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A sign of the SignSuisse lexicon. Each sign belongs to a single sign language: the same concept signed in
 * French and German Switzerland gives two signs, with different uids.
 *
 * @param uid SignSuisse identifier
 * @param language sign language of the sign
 * @param name the word or expression, as written by SignSuisse (usually upper case)
 * @param sourceUrl the SignSuisse page of the sign, or {@code null}
 * @param periphrase periphrase or synonyms, or {@code null}
 * @param definition definition, or {@code null}
 * @param status recognition status as published by SignSuisse (e.g. "Reconnu"), or {@code null}
 * @param categories thematic categories
 * @param videos videos of the sign itself
 * @param examples example sentences
 * @param images photos and illustrations of the sign
 */
public record Sign(
        String uid,
        Language language,
        String name,
        String sourceUrl,
        String periphrase,
        String definition,
        String status,
        List<Category> categories,
        List<Media> videos,
        List<Example> examples,
        List<Media> images) {

    public Sign {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(name, "name");
        categories = immutableCopy(categories);
        videos = immutableCopy(videos);
        examples = immutableCopy(examples);
        images = immutableCopy(images);
    }

    private static <T> List<T> immutableCopy(Collection<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
