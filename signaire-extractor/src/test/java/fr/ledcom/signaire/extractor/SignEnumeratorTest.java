package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

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
    void searchesOnlyTheBranchesAfterTheLastNameOfATruncatedSearch() {
        FakeLexicon lexicon = new FakeLexicon("SAA", "SAB", "SAC", "SAD", "SB", "SZ");

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.hits()).extracting(SearchHit::name).containsExactlyInAnyOrderElementsOf(lexicon.names);
        // "s" returns SAA, SAB, SAC: "sa" and the letters after "a" are left, nothing before
        assertThat(lexicon.queries).contains("sa", "sb", "sz").doesNotContain("s1", "s-", "saa");
    }

    @Test
    void needsFarFewerSearchesThanThereAreSigns() {
        List<String> names = IntStream.range(0, 2000)
                .mapToObj(i -> word(i * 7919L % 1_000_003L)).distinct().toList();
        FakeLexicon lexicon = new FakeLexicon(names.toArray(String[]::new));
        lexicon.cap = 100;

        SignEnumerator.Result result = new SignEnumerator(lexicon, 100, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.hits()).hasSameSizeAs(names);
        assertThat(result.queries()).isLessThan(names.size() / 5);
    }

    @Test
    void followsTheLastNameBeyondSpacesInsteadOfSearchingThem() {
        FakeLexicon lexicon = new FakeLexicon("A B", "A C", "A D", "A E", "AB");

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.hits()).extracting(SearchHit::name).containsExactlyInAnyOrderElementsOf(lexicon.names);
        assertThat(lexicon.queries).allSatisfy(q -> assertThat(q).isNotBlank().doesNotEndWith(" "))
                .doesNotHaveDuplicates()
                .contains("a d", "a e");
    }

    @Test
    void searchesCharactersLearnedFromTheNames() {
        FakeLexicon lexicon = new FakeLexicon("AB-A", "AB-B", "AB-C", "AB-D");

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.hits()).extracting(SearchHit::name).containsExactlyInAnyOrderElementsOf(lexicon.names);
    }

    @Test
    void reportsIncompleteEnumerationsWhenTheDepthLimitIsReached() {
        FakeLexicon lexicon = new FakeLexicon("AAA", "AAAA", "AAAAA", "AAAAAA", "AAAAAAA");

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 2).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.complete()).isFalse();
        assertThat(result.hits()).hasSizeLessThan(5);
    }

    @Test
    void refinesSearchesThatFail() {
        FakeLexicon lexicon = new FakeLexicon("BA", "BE", "BI");
        lexicon.failingQuery = "b";

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.hits()).extracting(SearchHit::name).containsExactlyInAnyOrder("BA", "BE", "BI");
        assertThat(result.complete()).isTrue();
        assertThat(result.failedQueries()).isEqualTo(1);
    }

    @Test
    void neverSearchesZeroOnWhichSignSuisseFails() {
        FakeLexicon lexicon = new FakeLexicon("ZEN");

        new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(lexicon.queries).doesNotContain("0").contains("1", "9");
    }

    @Test
    void handsEachSignOverAsSoonAsItIsFoundAndReportsEachSearch() {
        FakeLexicon lexicon = new FakeLexicon("AARAU", "BERNE", "SION", "ZOUG");
        List<String> events = new ArrayList<>();

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY,
                new SignEnumerator.Observer() {
                    @Override
                    public void found(SearchHit hit) {
                        events.add("found " + hit.name());
                    }

                    @Override
                    public void searched(String term, int queries, int pending, int found) {
                        events.add("searched " + term + " " + queries + " " + pending + " " + found);
                    }
                });

        // "a" finds AARAU: 34 initial characters are left; "b" finds BERNE
        assertThat(events).startsWith("found AARAU", "searched a 1 34 1", "found BERNE", "searched b 2 33 2");
        assertThat(events.getLast()).isEqualTo("searched 9 " + result.queries() + " 0 4");
    }

    @Test
    void keepsOnlyHitsInTheRequestedLanguage() {
        FakeLexicon lexicon = new FakeLexicon("ZURICH");
        lexicon.extraHit = new SearchHit("999", "ZÜRICH", Language.GERMAN, "https://example.org/999");

        SignEnumerator.Result result = new SignEnumerator(lexicon, CAP, 10).enumerate(Language.FRENCH, Category.CITY);

        assertThat(result.hits()).extracting(SearchHit::name).containsExactly("ZURICH");
    }

    /** A pronounceable word for a number: names of a large lexicon. */
    private static String word(long number) {
        String consonants = "BCDFGLMNPRSTV";
        String vowels = "AEIOU";
        StringBuilder word = new StringBuilder();
        long rest = number;
        do {
            word.append(consonants.charAt((int) (rest % consonants.length())));
            rest /= consonants.length();
            word.append(vowels.charAt((int) (rest % vowels.length())));
            rest /= vowels.length();
        } while (rest > 0);
        return word.toString();
    }

    /**
     * Mimics SignSuisse: accent and case insensitive substring search; the names starting with the term first, then
     * the others, each sorted; the list capped.
     */
    private static final class FakeLexicon implements SignSearch {
        private final List<String> names;
        private final List<String> queries = new ArrayList<>();
        private SearchHit extraHit;
        private String failingQuery;
        private int cap = CAP;

        FakeLexicon(String... names) {
            this.names = List.of(names);
        }

        @Override
        public List<SearchHit> search(String term, Language language, Category category) {
            queries.add(term);
            if (term.equals(failingQuery)) {
                throw new ExtractionException("HTTP status 500");
            }
            String normalizedTerm = SignEnumerator.normalize(term);
            List<SearchHit> hits = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (SignEnumerator.normalize(names.get(i)).contains(normalizedTerm)) {
                    hits.add(new SearchHit(String.valueOf(i), names.get(i), language, "https://example.org/" + i));
                }
            }
            if (extraHit != null) {
                hits.add(extraHit);
            }
            hits.sort(Comparator.comparing((SearchHit hit) -> !key(hit).startsWith(normalizedTerm))
                    .thenComparing(FakeLexicon::key));
            return hits.subList(0, Math.min(cap, hits.size()));
        }

        private static String key(SearchHit hit) {
            return Normalizer.normalize(hit.name(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        }
    }
}
