package fr.ledcom.signaire.web;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/**
 * The form of administrators to add or edit a sign. Media and examples are given one per line; media already
 * downloaded into a bundle are kept when their URL is kept.
 *
 * @param language code of the sign language (fr, de, it)
 * @param dialects separated by commas
 * @param videos URLs, one per line
 * @param images URLs, one per line
 * @param examples sentences, one per line
 */
public record SignForm(String name, String language, List<Category> categories, String periphrase,
        String definition, String dialects, String videos, String images, String examples, String sourceUrl) {

    /** A form for a new sign. */
    static SignForm empty(String name, String language) {
        return new SignForm(name, language, List.of(), "", "", "", "", "", "", "");
    }

    static SignForm of(Sign sign) {
        return new SignForm(sign.name(), sign.language().code(), sign.categories(), orEmpty(sign.periphrase()),
                orEmpty(sign.definition()), String.join(", ", sign.dialects()), urls(sign.videos()),
                urls(sign.images()), sign.examples().stream().map(Example::text).collect(Collectors.joining("\n")),
                orEmpty(sign.sourceUrl()));
    }

    /** Problems of the form: keys of messages, empty when the sign can be saved. */
    List<String> errors() {
        return Stream.of(
                name == null || name.isBlank() ? "admin.sign.error.name" : null,
                signLanguage().isEmpty() ? "admin.sign.error.language" : null)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * The sign described by the form.
     *
     * @param original the sign before the edition, whose media and examples are kept when unchanged; or null
     */
    Sign toSign(String uid, Sign original) {
        Map<String, Media> knownMedia = original == null ? Map.of()
                : Stream.concat(original.videos().stream(), original.images().stream())
                        .collect(Collectors.toMap(Media::sourceUrl, Function.identity(), (a, b) -> a));
        Map<String, Example> knownExamples = original == null ? Map.of()
                : original.examples().stream()
                        .collect(Collectors.toMap(Example::text, Function.identity(), (a, b) -> a));
        return Sign.builder(uid, signLanguage().orElseThrow(), name.strip())
                .sourceUrl(blankToNull(sourceUrl))
                .periphrase(blankToNull(periphrase))
                .definition(blankToNull(definition))
                .status(original == null ? null : original.status())
                .categories(categories == null ? List.of() : categories.stream().distinct().toList())
                .dialects(split(dialects, ",").toList())
                .videos(split(videos, "\n").map(url -> knownMedia.getOrDefault(url, new Media(url, null))).toList())
                .images(split(images, "\n").map(url -> knownMedia.getOrDefault(url, new Media(url, null))).toList())
                .examples(split(examples, "\n").map(text -> knownExamples.getOrDefault(text, new Example(text, null)))
                        .toList())
                .dialectVariants(original == null ? List.of() : original.dialectVariants())
                .build();
    }

    private Optional<Language> signLanguage() {
        return Arrays.stream(Language.values()).filter(value -> value.code().equals(language)).findFirst();
    }

    private static Stream<String> split(String text, String separator) {
        return text == null ? Stream.empty()
                : Arrays.stream(text.split(separator)).map(String::strip).filter(value -> !value.isEmpty());
    }

    private static String urls(Collection<Media> media) {
        return media.stream().map(Media::sourceUrl).collect(Collectors.joining("\n"));
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
