package fr.ledcom.signaire.extractor;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.model.TestSigns;

/** What the two forms of the fake SignSuisse share: its search over the test signs, its addresses and labels. */
final class FakeSearch {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NOT_A_LETTER = Pattern.compile("[^a-z0-9]+");
    /** The labels of the categories of the test signs, in the language of each sign. */
    private static final Map<Category, Map<Language, String>> CATEGORIES = Map.of(
            Category.CITY, Map.of(Language.FRENCH, "Ville", Language.GERMAN, "Stadt", Language.ITALIAN, "Città"),
            Category.ANIMALS, Map.of(Language.FRENCH, "Animaux", Language.GERMAN, "Tiere",
                    Language.ITALIAN, "Animali"),
            Category.NATURE, Map.of(Language.FRENCH, "Nature", Language.GERMAN, "Natur", Language.ITALIAN, "Natura"));

    private FakeSearch() {
    }

    /**
     * The search of SignSuisse: names containing the term, accents and case ignored, those starting with it first, each
     * part by name, cut at {@link SignSuisseClient#MAX_RESULTS}.
     *
     * @param language null for every sign language
     * @param category null for every category
     */
    static List<Sign> search(String term, @Nullable Language language, @Nullable Category category) {
        String wanted = fold(term).strip();
        if (wanted.isEmpty()) {
            return List.of();
        }
        return TestSigns.LEXICON.stream()
                .filter(sign -> language == null || sign.language() == language)
                .filter(sign -> category == null || sign.categories().contains(category))
                .filter(sign -> fold(sign.name()).contains(wanted))
                .sorted(Comparator.comparing((Sign sign) -> !fold(sign.name()).startsWith(wanted))
                        .thenComparing(sign -> fold(sign.name())))
                .limit(SignSuisseClient.MAX_RESULTS)
                .toList();
    }

    static Sign sign(String uid) {
        return TestSigns.LEXICON.stream().filter(sign -> sign.uid().equals(uid)).findFirst()
                .orElseThrow(() -> new ExtractionException("No sign " + uid + " in SignSuisse"));
    }

    /** The path of the page of a sign: SignSuisse's, with the path of the sign's language. */
    static String page(Sign sign) {
        String language = switch (sign.language()) {
            case FRENCH -> "/fr";
            case GERMAN -> "";
            case ITALIAN -> "/it";
        };
        return "/signsuisse" + language + "/lexikon/" + sign.uid() + "/" + slug(sign.name());
    }

    static String category(Category category, Language language) {
        Map<Language, String> labels = CATEGORIES.get(category);
        if (labels == null) {
            throw new IllegalArgumentException("No label for " + category + ": the test signs have none in it");
        }
        return labels.get(language);
    }

    static String fold(String text) {
        return DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
    }

    private static String slug(String name) {
        return NOT_A_LETTER.matcher(fold(name)).replaceAll("-");
    }
}
