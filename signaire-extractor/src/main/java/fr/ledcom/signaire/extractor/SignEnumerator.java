package fr.ledcom.signaire.extractor;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/**
 * Lists the signs of a language and category through the SignSuisse search, which returns at most a fixed number of
 * results, without paging.
 *
 * <p>The search returns the names starting with the term first, then the names containing it, each part sorted
 * (accents and case ignored, spaces before punctuation, digits, then letters), and cuts that list. Names are listed
 * by prefix: a search returning fewer results than the cap, or some names not starting with the prefix, lists every
 * name with that prefix. A search returning only names with the prefix, as many as the cap, lists them up to its last
 * name: the next character of that name after the prefix tells the searches left, that character and those sorting
 * after it. See {@code docs/design/extraction.md}: names that SignSuisse sorts otherwise may be missed.
 */
public class SignEnumerator {

    private static final Logger LOG = System.getLogger(SignEnumerator.class.getName());
    /** Not 0: SignSuisse always fails on that search. */
    private static final String INITIAL_ALPHABET = "abcdefghijklmnopqrstuvwxyz123456789";
    private static final String ALPHABET = INITIAL_ALPHABET + "0";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** Prefixes with too many names are searched up to this length on SignSuisse. */
    private static final int SIGNSUISSE_MAX_QUERY_LENGTH = 12;

    private final SignSearch search;
    private final int maxResults;
    private final int maxQueryLength;

    /**
     * @param search the search to enumerate
     * @param maxResults the number of results at which the search truncates its results
     * @param maxQueryLength prefixes are not searched beyond this length
     */
    public SignEnumerator(SignSearch search, int maxResults, int maxQueryLength) {
        this.search = search;
        this.maxResults = maxResults;
        this.maxQueryLength = maxQueryLength;
    }

    /** The enumeration of SignSuisse's search, which truncates its results at {@link SignSuisseClient#MAX_RESULTS}. */
    public static SignEnumerator forSignSuisse(SignSearch search) {
        return new SignEnumerator(search, SignSuisseClient.MAX_RESULTS, SIGNSUISSE_MAX_QUERY_LENGTH);
    }

    /**
     * Outcome of an enumeration.
     *
     * @param hits the signs found
     * @param queries number of searches sent
     * @param failedQueries number of searches that failed (their prefix was searched one character further)
     * @param complete false when some prefixes had too many names but reached the length limit
     */
    public record Result(List<SearchHit> hits, int queries, int failedQueries, boolean complete) {
        public Result {
            hits = List.copyOf(hits);
        }
    }

    /** Follows an enumeration. */
    public interface Observer {

        Observer NONE = new Observer() { };

        /** A search is about to be sent. */
        default void searching(String term) {
        }

        /** A sign seen for the first time: it can be extracted while the enumeration goes on. */
        default void found(SearchHit hit) {
        }

        /**
         * A search was done.
         *
         * @param queries the number of searches sent so far
         * @param pending the searches known to be left; it grows while names are found
         * @param found the number of signs found so far
         */
        default void searched(String term, int queries, int pending, int found) {
        }

        default void failed(String term, ExtractionException cause) {
        }
    }

    public Result enumerate(Language language, Category category) {
        return enumerate(language, category, Observer.NONE);
    }

    public Result enumerate(Language language, Category category, Observer observer) {
        return new Run(language, category, observer).run();
    }

    /** State of one enumeration. */
    private final class Run {
        private final Language language;
        private final Category category;
        private final Observer observer;
        private final Set<Character> alphabet = new TreeSet<>(SignEnumerator::compare);
        private final Deque<String> pending = new ArrayDeque<>();
        private final Set<String> queued = new HashSet<>();
        /** Prefixes searched after their last name, by the character of that name after them. */
        private final Map<String, Character> cursors = new LinkedHashMap<>();
        private final Map<String, SearchHit> hits = new LinkedHashMap<>();
        private int queries;
        private int failed;
        private boolean complete = true;

        Run(Language language, Category category, Observer observer) {
            this.language = language;
            this.category = category;
            this.observer = observer;
            for (char c : ALPHABET.toCharArray()) {
                alphabet.add(c);
            }
        }

        Result run() {
            for (char c : INITIAL_ALPHABET.toCharArray()) {
                queue(String.valueOf(c));
            }
            while (!pending.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Enumeration of " + category + " interrupted");
                }
                search(pending.poll());
            }
            return new Result(new ArrayList<>(hits.values()), queries, failed, complete);
        }

        private void search(String prefix) {
            queries++;
            observer.searching(prefix);
            List<SearchHit> results;
            try {
                results = search.search(prefix, language, category);
            } catch (ExtractionException e) {
                LOG.log(Level.INFO, "Search for \"{0}\" failed, searching one character further: {1}", prefix,
                        e.getMessage());
                failed++;
                observer.failed(prefix, e);
                after(prefix, Character.MIN_VALUE);
                observer.searched(prefix, queries, pending.size(), hits.size());
                return;
            }
            boolean onlyPrefixed = true;
            for (SearchHit hit : results) {
                onlyPrefixed &= normalize(hit.name()).startsWith(prefix);
                if (hit.language() == language && hits.putIfAbsent(hit.uid(), hit) == null) {
                    learnCharacters(hit.name());
                    observer.found(hit);
                }
            }
            if (results.size() >= maxResults && onlyPrefixed) {
                follow(prefix, normalize(results.get(results.size() - 1).name()));
            }
            observer.searched(prefix, queries, pending.size(), hits.size());
        }

        /** Queues the searches for the names with the prefix sorting after the cursor. */
        private void follow(String prefix, String cursor) {
            if (cursor.length() <= prefix.length()) {
                after(prefix, Character.MIN_VALUE);
                return;
            }
            char next = cursor.charAt(prefix.length());
            String branch = prefix + next;
            if (Character.isWhitespace(next)) {
                // the search ignores trailing spaces: the branch is followed one character further
                if (branch.length() < maxQueryLength) {
                    follow(branch, cursor);
                } else {
                    complete = false;
                }
            } else {
                queue(branch);
            }
            after(prefix, next);
        }

        /** Queues the prefix followed by each character of the alphabet sorting after the given one. */
        private void after(String prefix, char character) {
            if (prefix.length() >= maxQueryLength) {
                complete = false;
                return;
            }
            cursors.put(prefix, character);
            boolean noSpace = Character.isWhitespace(prefix.charAt(prefix.length() - 1));
            for (char c : alphabet) {
                if (compare(c, character) > 0 && !(noSpace && Character.isWhitespace(c))) {
                    queue(prefix + c);
                }
            }
        }

        private void queue(String prefix) {
            if (prefix.length() > maxQueryLength) {
                complete = false;
            } else if (queued.add(prefix)) {
                pending.add(prefix);
            }
        }

        private void learnCharacters(String name) {
            for (char c : normalize(name).toCharArray()) {
                if (!Character.isISOControl(c) && alphabet.add(c)) {
                    // prefixes followed before this character was known need it too
                    cursors.forEach((prefix, cursor) -> {
                        boolean noSpace = Character.isWhitespace(prefix.charAt(prefix.length() - 1));
                        if (compare(c, cursor) > 0 && !(noSpace && Character.isWhitespace(c))) {
                            queue(prefix + c);
                        }
                    });
                }
            }
        }
    }

    /** The order of SignSuisse, as far as known: spaces, punctuation, digits, letters, other letters. */
    static int compare(char a, char b) {
        int byRank = Integer.compare(rank(a), rank(b));
        return byRank == 0 ? Character.compare(a, b) : byRank;
    }

    private static int rank(char c) {
        if (c == Character.MIN_VALUE) {
            return -1;
        }
        if (Character.isWhitespace(c)) {
            return 0;
        }
        if (c >= '0' && c <= '9') {
            return 2;
        }
        if (c >= 'a' && c <= 'z') {
            return 3;
        }
        return Character.isLetter(c) ? 4 : 1;
    }

    /** A name or term as the search compares them: no accents, lower case, single spaces. */
    static String normalize(String text) {
        String folded = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(folded).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
