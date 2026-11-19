package fr.ledcom.signaire.cli;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fr.ledcom.signaire.extractor.MediaChoice;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Names of the media to download on the command line: shown in the user's language, accepted in any language. */
final class MediaNames {

    private static final Messages MESSAGES = Messages.of("fr.ledcom.signaire.cli.extract");

    private MediaNames() {
    }

    static String name(MediaChoice choice) {
        return MESSAGES.get("media." + choice.name());
    }

    /** Converts media choices given on the command line. */
    static final class Converter implements ITypeConverter<MediaChoice> {
        @Override
        public MediaChoice convert(String value) {
            String normalized = value.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(MediaChoice.values())
                    // the base bundle (ROOT) is French
                    .filter(choice -> Stream.of(Locale.ROOT, Locale.GERMAN, Locale.ITALIAN, Locale.ENGLISH)
                            .anyMatch(language -> MESSAGES.bundle(language).getString("media." + choice.name())
                                    .equals(normalized)))
                    .findFirst()
                    .orElseThrow(() -> new TypeConversionException(MESSAGES.get("error.unknownMedia", value,
                            Arrays.stream(MediaChoice.values()).map(MediaNames::name)
                                    .collect(Collectors.joining(", ")))));
        }
    }

    /** All media choices, for the help. */
    static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return Arrays.stream(MediaChoice.values()).map(MediaNames::name).iterator();
        }
    }
}
