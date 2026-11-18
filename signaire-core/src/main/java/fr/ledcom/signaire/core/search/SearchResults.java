package fr.ledcom.signaire.core.search;

import java.util.List;

import fr.ledcom.signaire.model.Sign;

/**
 * A page of search results.
 *
 * @param total number of signs matching the search
 * @param signs the signs of the requested page
 */
public record SearchResults(long total, List<Sign> signs) {

    public SearchResults {
        signs = List.copyOf(signs);
    }
}
