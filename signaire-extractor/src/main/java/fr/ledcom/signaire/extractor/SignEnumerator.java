package fr.ledcom.signaire.extractor;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/**
 * Finds all signs of a language and category through the SignSuisse search, which caps the number of results.
 *
 * <p>Queries start with single characters. A query returning the maximum number of results is refined by
 * appending each character of the alphabet. With substring matching this is exhaustive: following the
 * characters of a name eventually leads to a query matching that name and returning less than the maximum.
 * The alphabet starts with letters and digits and grows with the characters (spaces, apostrophes…) found in
 * the names of the signs.
 */
public class SignEnumerator {

    private static final String INITIAL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");

    private final SignSearch search;
    private final int maxResults;
    private final int maxQueryLength;

    /**
     * @param search the search to enumerate
     * @param maxResults the number of results at which the search truncates its results
     * @param maxQueryLength queries are not refined beyond this length
     */
    public SignEnumerator(SignSearch search, int maxResults, int maxQueryLength) {
        this.search = search;
        this.maxResults = maxResults;
        this.maxQueryLength = maxQueryLength;
    }

    /**
     * Outcome of an enumeration.
     *
     * @param hits the signs found
     * @param queries number of searches sent
     * @param complete false when some queries were truncated but could not be refined further
     */
    public record Result(List<SearchHit> hits, int queries, boolean complete) {
        public Result {
            hits = List.copyOf(hits);
        }
    }

    public Result enumerate(Language language, Category category) {
        return new Run(language, category).run();
    }

    /** State of one enumeration. */
    private final class Run {
        private final Language language;
        private final Category category;
        private final Set<Character> alphabet = new TreeSet<>();
        private final Deque<String> pending = new ArrayDeque<>();
        /** Number of results by query, as the search sees it (stripped). */
        private final Map<String, Integer> resultCounts = new HashMap<>();
        private final Set<String> truncated = new LinkedHashSet<>();
        private final Map<String, SearchHit> hits = new LinkedHashMap<>();
        private int queries;
        private boolean complete = true;

        Run(Language language, Category category) {
            this.language = language;
            this.category = category;
        }

        Result run() {
            for (char c : INITIAL_ALPHABET.toCharArray()) {
                alphabet.add(c);
                pending.add(String.valueOf(c));
            }
            while (!pending.isEmpty()) {
                String query = pending.poll();
                if (query.length() > maxQueryLength || truncated.contains(query)) {
                    continue;
                }
                if (resultCount(query) >= maxResults) {
                    truncated.add(query);
                    refine(query, alphabet);
                }
            }
            return new Result(new ArrayList<>(hits.values()), queries, complete);
        }

        private int resultCount(String query) {
            String key = query.strip();
            Integer known = resultCounts.get(key);
            if (known != null) {
                return known;
            }
            List<SearchHit> results = search.search(query, language, category);
            queries++;
            resultCounts.put(key, results.size());
            for (SearchHit hit : results) {
                if (hit.language() == language) {
                    hits.putIfAbsent(hit.uid(), hit);
                    learnCharacters(hit.name());
                }
            }
            return results.size();
        }

        private void refine(String query, Iterable<Character> characters) {
            if (query.length() >= maxQueryLength) {
                complete = false;
                return;
            }
            boolean endsWithSpace = Character.isWhitespace(query.charAt(query.length() - 1));
            for (char c : characters) {
                // the search ignores repeated spaces, refining with them would loop until the length limit
                if (!(endsWithSpace && Character.isWhitespace(c))) {
                    pending.add(query + c);
                }
            }
        }

        private void learnCharacters(String name) {
            List<Character> learned = new ArrayList<>();
            for (char c : normalize(name).toCharArray()) {
                if (!Character.isISOControl(c) && alphabet.add(c)) {
                    learned.add(c);
                }
            }
            if (!learned.isEmpty()) {
                // queries refined before these characters were known need refining with them too
                for (String query : List.copyOf(truncated)) {
                    refine(query, learned);
                }
            }
        }
    }

    static String normalize(String text) {
        return DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
    }
}
