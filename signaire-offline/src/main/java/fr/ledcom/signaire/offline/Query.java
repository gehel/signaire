package fr.ledcom.signaire.offline;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/**
 * A search of the library.
 *
 * @param text words to look for in names, periphrases, definitions and examples; {@code null} or blank to list the
 *             signs by name
 * @param languages sign languages to search, all when empty
 * @param categories categories to search, all when empty
 */
public record Query(@Nullable String text, Set<Language> languages, Set<Category> categories) {

    /** Every sign, by name. */
    public static final Query ALL = new Query(null, Set.of(), Set.of());

    public Query {
        text = text == null || text.isBlank() ? null : text.strip();
        languages = Set.copyOf(languages);
        categories = Set.copyOf(categories);
    }

    public static Query forText(@Nullable String text) {
        return new Query(text, Set.of(), Set.of());
    }
}
