package fr.ledcom.signaire.core.search;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/**
 * A search for signs.
 *
 * @param text words to look for in names, periphrases, definitions and examples; {@code null} or blank to list
 *             signs by name
 * @param languages sign languages to search, all when empty
 * @param categories categories to search, all when empty
 * @param from index of the first result to return
 * @param size maximum number of results to return
 */
public record SignQuery(@Nullable String text, Set<Language> languages, Set<Category> categories, int from,
        int size) {

    public static final int DEFAULT_SIZE = 20;

    public SignQuery {
        text = text == null || text.isBlank() ? null : text.strip();
        languages = languages == null ? Set.of() : Set.copyOf(languages);
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        if (from < 0 || size < 0) {
            throw new IllegalArgumentException("from and size must be positive");
        }
    }

    public static SignQuery forText(String text) {
        return new SignQuery(text, Set.of(), Set.of(), 0, DEFAULT_SIZE);
    }

    public static SignQuery all(int from, int size) {
        return new SignQuery(null, Set.of(), Set.of(), from, size);
    }
}
