package fr.ledcom.signaire.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import fr.ledcom.signaire.model.Category;
import picocli.CommandLine.TypeConversionException;

class TranslationsTest {

    private static final List<String> BUNDLES = List.of("messages", "extract", "load", "search", "export", "categories");

    static Stream<Arguments> translations() {
        return BUNDLES.stream().flatMap(bundle -> Stream.of("de", "it", "en").map(lang -> Arguments.of(bundle, lang)));
    }

    @ParameterizedTest
    @MethodSource("translations")
    void translationsHaveTheKeysOfTheFrenchBundle(String bundle, String language) throws IOException {
        Properties french = load(bundle + ".properties");
        Properties translated = load(bundle + "_" + language + ".properties");

        assertThat(translated.stringPropertyNames()).isEqualTo(french.stringPropertyNames());
    }

    @Test
    void messagesFollowTheDefaultLocale() {
        try (DefaultLocale _ = DefaultLocale.set(Locale.GERMAN)) {
            assertThat(Messages.of("fr.ledcom.signaire.cli.load").get("load.unchanged", "x")).isEqualTo("x: unverändert");
        }
        try (DefaultLocale _ = DefaultLocale.set(Locale.ITALIAN)) {
            assertThat(Messages.of("fr.ledcom.signaire.cli.load").get("load.unchanged", "x")).isEqualTo("x: invariato");
        }
        try (DefaultLocale _ = DefaultLocale.set(Locale.ENGLISH)) {
            assertThat(Messages.of("fr.ledcom.signaire.cli.load").get("load.unchanged", "x")).isEqualTo("x: unchanged");
        }
    }

    @Test
    void unsupportedLanguagesFallBackToFrench() {
        try (DefaultLocale _ = DefaultLocale.set(Locale.of("es", "ES"))) {
            assertThat(Messages.of("fr.ledcom.signaire.cli.load").get("load.unchanged", "x")).isEqualTo("x : inchangé");
        }
    }

    @Test
    void categoryNamesFollowTheDefaultLocale() {
        try (DefaultLocale _ = DefaultLocale.set(Locale.GERMAN)) {
            assertThat(CategoryNames.name(Category.CITY)).isEqualTo("stadt");
            assertThat(new CategoryNames.Candidates()).contains("stadt", "beruf-schule").doesNotContain("ville");
        }
        try (DefaultLocale _ = DefaultLocale.set(Locale.ITALIAN)) {
            assertThat(CategoryNames.name(Category.CITY)).isEqualTo("citta");
        }
        try (DefaultLocale _ = DefaultLocale.set(Locale.ENGLISH)) {
            assertThat(CategoryNames.name(Category.CITY)).isEqualTo("city");
        }
    }

    @Test
    void categoryNamesAreAcceptedInEveryLanguage() {
        CategoryNames.Converter converter = new CategoryNames.Converter();

        assertThat(List.of("ville", "stadt", "citta", "city", "Stadt"))
                .allSatisfy(name -> assertThat(converter.convert(name)).isEqualTo(Category.CITY));
    }

    @Test
    void unknownCategoriesAreReportedInTheUserLanguage() {
        try (DefaultLocale _ = DefaultLocale.set(Locale.GERMAN)) {
            CategoryNames.Converter converter = new CategoryNames.Converter();
            assertThatThrownBy(() -> converter.convert("xyz"))
                    .isInstanceOf(TypeConversionException.class)
                    .hasMessage("Unbekannte Kategorie: xyz");
        }
    }

    @Test
    void aCategoryNameNeverDesignatesTwoCategories() throws IOException {
        Map<String, List<String>> categoriesByName = new HashMap<>();
        for (String file : List.of("categories.properties", "categories_de.properties", "categories_it.properties",
                "categories_en.properties")) {
            Properties names = load(file);
            for (String category : names.stringPropertyNames()) {
                categoriesByName.computeIfAbsent(names.getProperty(category), name -> new ArrayList<>()).add(category);
            }
        }

        assertThat(categoriesByName).allSatisfy((name, categories) -> assertThat(categories).as(name).containsOnly(
                categories.getFirst()));
    }

    private static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = TranslationsTest.class.getResourceAsStream(file)) {
            assertThat(in).as(file).isNotNull();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
