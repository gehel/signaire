package fr.ledcom.signaire.cli;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;

/**
 * Formats the messages of a resource bundle in the user's language: the default locale when the message is
 * formatted, like picocli does for the help. The base bundles are French, and the fallback for languages
 * without a translation.
 */
final class Messages {

    /** Falls back to the base (French) bundle rather than to the bundle of the default locale. */
    private static final Control BASE_FALLBACK = Control.getNoFallbackControl(Control.FORMAT_PROPERTIES);

    private final String bundleName;

    private Messages(String bundleName) {
        this.bundleName = bundleName;
    }

    static Messages of(String bundleName) {
        return new Messages(bundleName);
    }

    String get(String key, Object... arguments) {
        Locale locale = Locale.getDefault();
        return new MessageFormat(bundle(locale).getString(key), locale).format(arguments);
    }

    ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(bundleName, locale, BASE_FALLBACK);
    }
}
