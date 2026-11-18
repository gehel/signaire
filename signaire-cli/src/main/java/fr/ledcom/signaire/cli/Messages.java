package fr.ledcom.signaire.cli;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/** Formats the messages of a resource bundle, in the locale of the bundle. */
final class Messages {

    private final ResourceBundle bundle;

    Messages(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    static Messages of(String bundleName) {
        // the user's locale, like picocli does for the help
        return new Messages(ResourceBundle.getBundle(bundleName, Locale.getDefault()));
    }

    String get(String key, Object... arguments) {
        return new MessageFormat(bundle.getString(key), bundle.getLocale()).format(arguments);
    }

    boolean contains(String key) {
        return bundle.containsKey(key);
    }
}
