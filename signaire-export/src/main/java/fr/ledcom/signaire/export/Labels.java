package fr.ledcom.signaire.export;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/** The texts of exported documents, in the language of the request; French when it has no translation. */
final class Labels {

    private static final Control BASE_FALLBACK = Control.getNoFallbackControl(Control.FORMAT_PROPERTIES);

    private final ResourceBundle bundle;
    private final Locale locale;

    private Labels(ResourceBundle bundle, Locale locale) {
        this.bundle = bundle;
        this.locale = locale;
    }

    static Labels of(Locale locale) {
        return new Labels(ResourceBundle.getBundle("fr.ledcom.signaire.export.export", locale, BASE_FALLBACK), locale);
    }

    String get(String key, Object... arguments) {
        String pattern = bundle.getString(key);
        return arguments.length == 0 ? pattern : new MessageFormat(pattern, locale).format(arguments);
    }

    String category(Category category) {
        return get("category." + category.name());
    }

    String signLanguage(Language language) {
        return get("signLanguage." + language.name());
    }

    String signLanguageShort(Language language) {
        return get("signLanguage.short." + language.name());
    }

    /** Language code of the labels, for document metadata. */
    String language() {
        String language = bundle.getLocale().getLanguage();
        return language.isEmpty() ? "fr" : language;
    }
}
