package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessagesTest {

    @ParameterizedTest
    @ValueSource(strings = {"de", "it", "en"})
    void translationsHaveTheKeysOfTheFrenchMessages(String language) throws IOException {
        Properties french = load("/messages.properties");
        Properties translated = load("/messages_" + language + ".properties");

        assertThat(translated.stringPropertyNames()).isEqualTo(french.stringPropertyNames());
    }

    @Test
    void everySupportedLanguageHasTranslations() {
        assertThat(LocaleConfiguration.SUPPORTED_LOCALES).containsExactly(Locale.FRENCH, Locale.GERMAN,
                Locale.ITALIAN, Locale.ENGLISH);
        assertThat(LocaleConfiguration.SUPPORTED_LOCALES.subList(1, LocaleConfiguration.SUPPORTED_LOCALES.size()))
                .allSatisfy(locale -> assertThat(MessagesTest.class.getResource(
                        "/messages_" + locale.getLanguage() + ".properties")).isNotNull());
    }

    private static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream(file)) {
            assertThat(in).as(file).isNotNull();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
