package fr.ledcom.signaire.cli;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

import fr.ledcom.signaire.model.Language;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Sign languages on the command line, given by their codes (fr, de, it). */
final class LanguageCodes {

    private static final Messages ERRORS = Messages.of("fr.ledcom.signaire.cli.extract");

    private LanguageCodes() {
    }

    /** Converts language codes given on the command line. */
    static final class Converter implements ITypeConverter<Language> {
        @Override
        public Language convert(String value) {
            String code = value.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(Language.values())
                    .filter(language -> language.code().equals(code))
                    .findFirst()
                    .orElseThrow(() -> new TypeConversionException(ERRORS.get("error.unknownLanguage", value)));
        }
    }

    /** All language codes, for the help. */
    static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return Arrays.stream(Language.values()).map(Language::code).iterator();
        }
    }
}
