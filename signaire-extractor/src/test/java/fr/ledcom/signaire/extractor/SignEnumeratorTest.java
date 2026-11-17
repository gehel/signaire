package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

class SignEnumeratorTest {

    private static final int CAP = 3;

    @Test
    void findsAllSignsDespiteTheResultCap() {
        FakeLexicon lexicon = new FakeLexicon(
                "AARAU", "ABU DHABI", "AIGLE", "BÂLE", "BERNE", "BIENNE", "GENÈVE", "LAUSANNE", "LUCERNE",
                "LUGANO", "NEUCHÂTEL", "SION", "À 10 HEURES", "ZOUG", "ZURICH", "L'ABBAYE", "A", "Z-9");

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.hits()).extracting(SearchHit::name).containsExactlyInAnyOrderElementsOf(lexicon.names);
        assertThat(result.complete()).isTrue();
        assertThat(result.queries()).isEqualTo(lexicon.queries.size());
    }

    @Test
    void neverSendsBlankQueries() {
        FakeLexicon lexicon = new FakeLexicon("A B", "A C", "A D", "A E", "AB");

        new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(lexicon.queries).allSatisfy(q -> assertThat(q).isNotBlank());
        assertThat(lexicon.queries).doesNotHaveDuplicates();
    }

    @Test
    void reportsIncompleteEnumerationsWhenTheDepthLimitIsReached() {
        FakeLexicon lexicon = new FakeLexicon("AAA", "AAAA", "AAAAA", "AAAAAA", "AAAAAAA");

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 2).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.complete()).isFalse();
        assertThat(result.hits()).hasSizeLessThan(5);
    }

    @Test
    void refinesQueriesThatFail() {
        FakeLexicon lexicon = new FakeLexicon("ZERO 0", "ZÉRO 0 0", "ZEN");
        lexicon.failingQuery = "0";

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.hits()).extracting(SearchHit::name).containsExactlyInAnyOrder("ZERO 0", "ZÉRO 0 0", "ZEN");
        assertThat(result.complete()).isTrue();
        assertThat(lexicon.queries).contains("0", "00").doesNotContain("0 0");
    }

    @Test
    void keepsOnlyHitsInTheRequestedLanguage() {
        FakeLexicon lexicon = new FakeLexicon("ZURICH");
        lexicon.extraHit = new SearchHit("999", "ZÜRICH", Language.GERMAN, "https://example.org/999");

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.hits()).extracting(SearchHit::name).containsExactly("ZURICH");
    }

    /** Mimics SignSuisse: accent and case insensitive substring search, results sorted and capped. */
    private static final class FakeLexicon implements SignSearch {
        private final List<String> names;
        private final List<String> queries = new ArrayList<>();
        private SearchHit extraHit;
        private String failingQuery;

        FakeLexicon(String... names) {
            this.names = List.of(names);
        }

        @Override
        public List<SearchHit> search(String term, Language language, Category category) {
            queries.add(term);
            if (term.equals(failingQuery)) {
                throw new ExtractionException("HTTP status 500");
            }
            String normalizedTerm = normalize(term.strip());
            List<SearchHit> hits = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (normalize(names.get(i)).contains(normalizedTerm)) {
                    hits.add(new SearchHit(String.valueOf(i), names.get(i), language, "https://example.org/" + i));
                }
            }
            if (extraHit != null) {
                hits.add(extraHit);
            }
            hits.sort(Comparator.comparing(SearchHit::name));
            return hits.subList(0, Math.min(CAP, hits.size()));
        }

        private static String normalize(String s) {
            return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        }
    }
}
