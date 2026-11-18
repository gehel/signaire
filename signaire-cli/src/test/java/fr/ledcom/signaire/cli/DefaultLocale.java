package fr.ledcom.signaire.cli;

import java.util.Locale;

/** Changes the JVM default locale, the language of the CLI, until closed. */
final class DefaultLocale implements AutoCloseable {

    private final Locale previous;

    private DefaultLocale(Locale locale) {
        previous = Locale.getDefault();
        Locale.setDefault(locale);
    }

    static DefaultLocale set(Locale locale) {
        return new DefaultLocale(locale);
    }

    @Override
    public void close() {
        Locale.setDefault(previous);
    }
}
