package fr.ledcom.signaire.model;

import java.util.Locale;

/** The sign languages of Switzerland, identified by the language code SignSuisse uses for them. */
public enum Language {
    /** Langue des signes française (LSF-SR), Romandy. */
    FRENCH("fr"),
    /** Deutschschweizerische Gebärdensprache (DSGS). */
    GERMAN("de"),
    /** Lingua dei segni italiana (LIS-SI), Ticino. */
    ITALIAN("it");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Language fromCode(String code) {
        String normalized = code.toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unknown sign language code: " + code);
    }
}
