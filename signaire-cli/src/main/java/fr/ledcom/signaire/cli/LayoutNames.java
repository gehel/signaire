package fr.ledcom.signaire.cli;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Stream;

import fr.ledcom.signaire.export.PdfLayout;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Names of the PDF layouts on the command line: shown in the user's language, accepted in any language. */
final class LayoutNames {

    private static final Messages MESSAGES = Messages.of("fr.ledcom.signaire.cli.export");

    private LayoutNames() {
    }

    static String name(PdfLayout.Mode mode) {
        return MESSAGES.get("layout." + mode.name());
    }

    /** Converts layout names given on the command line. */
    static final class Converter implements ITypeConverter<PdfLayout.Mode> {
        @Override
        public PdfLayout.Mode convert(String value) {
            String normalized = value.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(PdfLayout.Mode.values())
                    // the base bundle (ROOT) is French
                    .filter(mode -> Stream.of(Locale.ROOT, Locale.GERMAN, Locale.ITALIAN, Locale.ENGLISH)
                            .anyMatch(language -> MESSAGES.bundle(language).getString("layout." + mode.name())
                                    .equals(normalized)))
                    .findFirst()
                    .orElseThrow(() -> new TypeConversionException(MESSAGES.get("error.unknownLayout", value)));
        }
    }

    /** All layout names, for the help. */
    static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return Arrays.stream(PdfLayout.Mode.values()).map(LayoutNames::name).iterator();
        }
    }
}
