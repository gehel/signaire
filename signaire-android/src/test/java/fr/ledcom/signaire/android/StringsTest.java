package fr.ledcom.signaire.android;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;

/** The app speaks French, German, Italian and English: each translation has every text of the French. */
class StringsTest {

    private static final Path RESOURCES = Path.of("src/main/res");

    @ParameterizedTest
    @ValueSource(strings = {"values-de", "values-it", "values-en"})
    void translationsHaveEveryTextOfTheFrench(String translation) throws Exception {
        assertThat(names(translation)).isEqualTo(names("values"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"values", "values-de", "values-it", "values-en"})
    void categoriesSignLanguagesAndMediaAreNamed(String language) throws Exception {
        Set<String> names = names(language);
        Arrays.stream(Category.values()).forEach(category -> assertThat(names).contains("category_" + category));
        for (Language sign : Language.values()) {
            assertThat(names).contains("sign_language_" + sign, "sign_language_short_" + sign);
        }
        Arrays.stream(MediaChoice.values()).forEach(media -> assertThat(names).contains("download_media_" + media));
    }

    /** The names of the strings and plurals of a directory of values. */
    private static Set<String> names(String directory) throws Exception {
        Set<String> names = new HashSet<>();
        for (String file : List.of("strings.xml", "labels.xml")) {
            Path path = RESOURCES.resolve(directory).resolve(file);
            assertThat(path).exists();
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            try (var in = Files.newInputStream(path)) {
                Element resources = factory.newDocumentBuilder().parse(in).getDocumentElement();
                for (String tag : List.of("string", "plurals")) {
                    NodeList elements = resources.getElementsByTagName(tag);
                    for (int i = 0; i < elements.getLength(); i++) {
                        names.add(((Element) elements.item(i)).getAttribute("name"));
                    }
                }
            }
        }
        return names;
    }
}
