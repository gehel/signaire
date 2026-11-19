package fr.ledcom.signaire.extractor;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
 * the names of the signs. Queries that fail are refined like truncated ones.
 */
public class SignEnumerator {

    private static final Logger LOG = System.getLogger(SignEnumerator.class.getName());
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

    /** Follows an enumeration, which can take hundreds of searches. */
    @FunctionalInterface
    public interface SearchProgress {

        SearchProgress NONE = (queries, found) -> { };

        /**
         * Called after each search.
         *
         * @param queries the number of searches sent so far
         * @param found the number of signs found so far
         */
        void searched(int queries, int found);
    }

    public Result enumerate(Language language, Category category) {
        return enumerate(language, category, SearchProgress.NONE);
    }

    public Result enumerate(Language language, Category category, SearchProgress progress) {
        return new Run(language, category, progress).run();
    }

    /** State of one enumeration. */
    private final class Run {
        private final Language language;
        private final Category category;
        private final SearchProgress progress;
        private final Set<Character> alphabet = new TreeSet<>();
        private final Deque<String> pending = new ArrayDeque<>();
        /** Number of results by query, as the search sees it (stripped). */
        private final Map<String, Integer> resultCounts = new HashMap<>();
        private final Set<String> truncated = new LinkedHashSet<>();
        /** Queries the search failed on (stripped). */
        private final Set<String> failed = new HashSet<>();
        private final Map<String, SearchHit> hits = new LinkedHashMap<>();
        private int queries;
        private boolean complete = true;

        Run(Language language, Category category, SearchProgress progress) {
            this.language = language;
            this.category = category;
            this.progress = progress;
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
            queries++;
            int count = search(query);
            resultCounts.put(key, count);
            progress.searched(queries, hits.size());
            return count;
        }

        /** The number of results of a search, the maximum when it fails. */
        private int search(String query) {
            List<SearchHit> results;
            try {
                results = search.search(query, language, category);
            } catch (ExtractionException e) {
                // e.g. SignSuisse always fails on "0": the longer queries starting with it find the same signs
                LOG.log(Level.INFO, "Search for \"{0}\" failed, searching longer queries instead: {1}", query,
                        e.getMessage());
                failed.add(query.strip());
                return maxResults;
            }
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
            // the search ignores trailing and repeated spaces: refining a failed query or a query ending with a
            // space with a space would repeat the same search until the length limit
            boolean noSpace = Character.isWhitespace(query.charAt(query.length() - 1)) || failed.contains(query.strip());
            for (char c : characters) {
                if (!(noSpace && Character.isWhitespace(c))) {
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
