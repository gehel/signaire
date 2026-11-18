package fr.ledcom.signaire.cli;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Stream;

import fr.ledcom.signaire.model.Category;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Names of the categories on the command line, defined in the {@code categories} resource bundles. The names
 * are shown in the user's language, and accepted in any language.
 */
final class CategoryNames {

    private static final Messages NAMES = Messages.of("fr.ledcom.signaire.cli.categories");
    private static final Messages ERRORS = Messages.of("fr.ledcom.signaire.cli.extract");

    private CategoryNames() {
    }

    static String name(Category category) {
        return NAMES.get(category.name());
    }

    /** Converts category names given on the command line. */
    static final class Converter implements ITypeConverter<Category> {
        @Override
        public Category convert(String value) {
            String normalized = value.strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(Category.values())
                    // the base bundle (ROOT) is French
                    .filter(category -> Stream.of(Locale.ROOT, Locale.GERMAN, Locale.ITALIAN, Locale.ENGLISH)
                            .anyMatch(language -> NAMES.bundle(language).getString(category.name()).equals(normalized)))
                    .findFirst()
                    .orElseThrow(() -> new TypeConversionException(ERRORS.get("error.unknownCategory", value)));
        }
    }

    /** All category names, for the help. */
    static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return Arrays.stream(Category.values()).map(CategoryNames::name).iterator();
        }
    }
}
