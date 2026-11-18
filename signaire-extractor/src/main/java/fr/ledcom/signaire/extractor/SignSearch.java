package fr.ledcom.signaire.extractor;

import java.util.List;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/** The SignSuisse search: fuzzy, accent insensitive, and limited to a maximum number of results. */
@FunctionalInterface
public interface SignSearch {

    List<SearchHit> search(String term, Language language, Category category);
}
