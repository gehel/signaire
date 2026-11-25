package fr.ledcom.signaire.offline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Sign;

/**
 * A sign as the search sees it: its texts folded and cut into words, once, when the bundles load.
 *
 * @param name the folded name, by which signs are sorted
 * @param texts the words of the name, the periphrase and the definition
 * @param all those words and the words of the examples
 */
record Entry(Sign sign, String name, List<String> nameWords, List<String> texts, List<String> all) {

    /** For a query of several words, how good a match: lower is better. */
    static final int NO_MATCH = Integer.MAX_VALUE;

    Entry {
        nameWords = List.copyOf(nameWords);
        texts = List.copyOf(texts);
        all = List.copyOf(all);
    }

    static Entry of(Sign sign) {
        String name = Folding.fold(sign.name());
        List<String> nameWords = Folding.words(name);
        List<String> texts = new ArrayList<>(nameWords);
        texts.addAll(Folding.words(Folding.fold(sign.periphrase())));
        texts.addAll(Folding.words(Folding.fold(sign.definition())));
        List<String> all = new ArrayList<>(texts);
        for (Example example : sign.examples()) {
            all.addAll(Folding.words(Folding.fold(example.text())));
        }
        return new Entry(sign, name, nameWords, texts, all);
    }

    /**
     * The rank of the sign for folded words: the name itself, then names starting with them, then names, periphrases
     * and definitions with words starting with each, then examples too; {@link #NO_MATCH} otherwise.
     */
    int rank(String text, List<String> words) {
        if (name.equals(text)) {
            return 0;
        }
        if (name.startsWith(text)) {
            return 1;
        }
        if (startWords(words, nameWords)) {
            return 2;
        }
        if (startWords(words, texts)) {
            return 3;
        }
        return startWords(words, all) ? 4 : NO_MATCH;
    }

    private static boolean startWords(Iterable<String> words, Collection<String> candidates) {
        for (String word : words) {
            if (candidates.stream().noneMatch(candidate -> candidate.startsWith(word))) {
                return false;
            }
        }
        return true;
    }
}
