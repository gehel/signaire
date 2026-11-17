package fr.ledcom.signaire.extractor;

import java.nio.file.Path;
import java.util.List;

import fr.ledcom.signaire.model.Category;

/**
 * Outcome of the extraction of a category.
 *
 * @param category the extracted category
 * @param bundle the bundle written
 * @param signCount number of signs in the bundle
 * @param failures signs found by the search but that could not be extracted
 * @param enumerationComplete false when the search may have missed signs
 */
public record ExtractionReport(
        Category category, Path bundle, int signCount, List<SearchHit> failures, boolean enumerationComplete) {

    public ExtractionReport {
        failures = List.copyOf(failures);
    }
}
