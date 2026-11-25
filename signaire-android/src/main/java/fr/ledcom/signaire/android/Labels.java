package fr.ledcom.signaire.android;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/**
 * The names of categories and sign languages, in the language of the phone: {@code labels.xml}, as the web application
 * names them. StringsTest checks that each one is there.
 */
final class Labels {

    private Labels() {
    }

    static String category(Context context, Category category) {
        return text(context, "category_" + category.name());
    }

    static String signLanguage(Context context, Language language) {
        return text(context, "sign_language_" + language.name());
    }

    /** LSF, DSGS, LIS. */
    static String signLanguageShort(Context context, Language language) {
        return text(context, "sign_language_short_" + language.name());
    }

    /** The categories in the alphabetical order of their names. */
    static List<Category> categoriesByName(Context context) {
        Collator collator = Collator.getInstance(locale(context));
        return Arrays.stream(Category.values())
                .sorted(Comparator.comparing(category -> category(context, category), collator))
                .toList();
    }

    /** The sign language of the language of the phone: LSF in French, DSGS in German, LIS in Italian. */
    static Language signLanguageOfThePhone(Context context) {
        return switch (locale(context).getLanguage()) {
            case "de" -> Language.GERMAN;
            case "it" -> Language.ITALIAN;
            default -> Language.FRENCH;
        };
    }

    static Locale locale(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    private static String text(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
        return id == 0 ? name : context.getString(id);
    }
}
