package fr.ledcom.signaire.model;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A sign of the SignSuisse lexicon. Each sign belongs to a single sign language: the same concept signed in
 * French and German Switzerland gives two signs, with different uids.
 *
 * <p>Free text attributes are kept as SignSuisse publishes them, in the written language matching the sign
 * language.
 *
 * @param uid SignSuisse identifier
 * @param language sign language of the sign
 * @param name the word or expression, as written by SignSuisse (usually upper case)
 * @param sourceUrl the SignSuisse page of the sign, or {@code null}
 * @param periphrase periphrase or synonyms, or {@code null}
 * @param definition definition, or {@code null}
 * @param status recognition status (e.g. "Reconnu", "Anerkannt"), or {@code null}
 * @param categories thematic categories
 * @param dialects regions where the sign is used (e.g. "BE", "ZH"); empty when unspecified
 * @param videos videos of the sign itself
 * @param examples example sentences
 * @param images photos and illustrations of the sign
 * @param dialectVariants regional variants of the sign
 */
public record Sign(
        String uid,
        Language language,
        String name,
        @Nullable String sourceUrl,
        @Nullable String periphrase,
        @Nullable String definition,
        @Nullable String status,
        List<Category> categories,
        List<String> dialects,
        List<Media> videos,
        List<Example> examples,
        List<Media> images,
        List<DialectVariant> dialectVariants) {

    public Sign {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(name, "name");
        categories = immutableCopy(categories);
        dialects = immutableCopy(dialects);
        videos = immutableCopy(videos);
        examples = immutableCopy(examples);
        images = immutableCopy(images);
        dialectVariants = immutableCopy(dialectVariants);
    }

    public static Builder builder(String uid, Language language, String name) {
        return new Builder(uid, language, name);
    }

    public Builder toBuilder() {
        return new Builder(uid, language, name)
                .sourceUrl(sourceUrl)
                .periphrase(periphrase)
                .definition(definition)
                .status(status)
                .categories(categories)
                .dialects(dialects)
                .videos(videos)
                .examples(examples)
                .images(images)
                .dialectVariants(dialectVariants);
    }

    /** Lists missing from a bundle's JSON come as {@code null}. */
    private static <T> List<T> immutableCopy(@Nullable Collection<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }

    /** Builds signs, all attributes but the uid, language and name being optional. */
    public static final class Builder {
        private final String uid;
        private final Language language;
        private final String name;
        private @Nullable String sourceUrl;
        private @Nullable String periphrase;
        private @Nullable String definition;
        private @Nullable String status;
        private List<Category> categories = List.of();
        private List<String> dialects = List.of();
        private List<Media> videos = List.of();
        private List<Example> examples = List.of();
        private List<Media> images = List.of();
        private List<DialectVariant> dialectVariants = List.of();

        private Builder(String uid, Language language, String name) {
            this.uid = uid;
            this.language = language;
            this.name = name;
        }

        public Builder sourceUrl(@Nullable String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }

        public Builder periphrase(@Nullable String periphrase) {
            this.periphrase = periphrase;
            return this;
        }

        public Builder definition(@Nullable String definition) {
            this.definition = definition;
            return this;
        }

        public Builder status(@Nullable String status) {
            this.status = status;
            return this;
        }

        public Builder categories(List<Category> categories) {
            this.categories = categories;
            return this;
        }

        public Builder dialects(List<String> dialects) {
            this.dialects = dialects;
            return this;
        }

        public Builder videos(List<Media> videos) {
            this.videos = videos;
            return this;
        }

        public Builder examples(List<Example> examples) {
            this.examples = examples;
            return this;
        }

        public Builder images(List<Media> images) {
            this.images = images;
            return this;
        }

        public Builder dialectVariants(List<DialectVariant> dialectVariants) {
            this.dialectVariants = dialectVariants;
            return this;
        }

        public Sign build() {
            return new Sign(uid, language, name, sourceUrl, periphrase, definition, status,
                    categories, dialects, videos, examples, images, dialectVariants);
        }
    }
}
