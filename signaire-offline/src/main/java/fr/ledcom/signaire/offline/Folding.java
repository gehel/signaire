package fr.ledcom.signaire.offline;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/** Text as the search compares it: lower case, without accents, words separated by single spaces. */
final class Folding {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private Folding() {
    }

    static String fold(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String plain = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT)
                // letters that don't decompose
                .replace("ß", "ss").replace("œ", "oe").replace("æ", "ae");
        return NOT_WORD.matcher(plain).replaceAll(" ").strip();
    }

    static List<String> words(String folded) {
        return folded.isEmpty() ? List.of() : List.of(folded.split(" "));
    }
}
