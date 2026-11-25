package fr.ledcom.signaire.web;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import fr.ledcom.signaire.model.Sign;

/**
 * Signs under their initial, as in a dictionary: the results of a search listed by name, where it says where one is
 * in the alphabet.
 *
 * @param letter the initial, without accent; {@code #} for names that don't start with a letter; null for signs
 *               found by words, in the order of relevance
 */
public record Initial(String letter, List<Sign> signs) {

    public Initial {
        signs = List.copyOf(signs);
    }

    /** Signs listed by name under their initials, or found by words in one group without initial. */
    static List<Initial> group(List<Sign> signs, boolean byName) {
        if (signs.isEmpty()) {
            return List.of();
        }
        if (!byName) {
            return List.of(new Initial(null, signs));
        }
        List<Initial> initials = new ArrayList<>();
        String letter = null;
        List<Sign> group = new ArrayList<>();
        for (Sign sign : signs) {
            String initial = initial(sign.name());
            if (!initial.equals(letter) && !group.isEmpty()) {
                initials.add(new Initial(letter, group));
                group = new ArrayList<>();
            }
            letter = initial;
            group.add(sign);
        }
        initials.add(new Initial(letter, group));
        return initials;
    }

    /** The first letter of a name without its accent, as the index sorts names. */
    private static String initial(String name) {
        String folded = Normalizer.normalize(name.strip(), Normalizer.Form.NFD);
        if (folded.isEmpty() || !Character.isLetter(folded.charAt(0))) {
            return "#";
        }
        return folded.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
