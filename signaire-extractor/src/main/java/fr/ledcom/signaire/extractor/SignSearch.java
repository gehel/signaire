package fr.ledcom.signaire.extractor;

import java.util.List;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/** The SignSuisse search: fuzzy, accent insensitive, and limited to a maximum number of results. */
@FunctionalInterface
public interface SignSearch {

    /**
     * @param category the category to search, or {@code null} to search them all
     */
    List<SearchHit> search(String term, Language language, Category category);
}
