package fr.ledcom.signaire.web;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import fr.ledcom.signaire.extractor.ExtractionException;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/** Replaces SignSuisse in the tests: two French signs in the CITY category, ZOUG and ZURICH. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeSignSuisse {

    static final SearchHit ZOUG = new SearchHit("900", "ZOUG", Language.FRENCH, "https://example.org/fr/900/zoug");
    static final SearchHit ZURICH = new SearchHit("901", "ZURICH", Language.FRENCH,
            "https://example.org/fr/901/zurich");

    @Bean
    @Primary
    SignSource fakeSignSuisse() {
        return new SignSource() {
            @Override
            public List<SearchHit> search(String term, Language language, Category category) {
                boolean city = category == null || category == Category.CITY;
                if (!city || language != Language.FRENCH) {
                    return List.of();
                }
                return List.of(ZOUG, ZURICH).stream()
                        .filter(hit -> hit.name().toLowerCase(Locale.ROOT).contains(
                                term.strip().toLowerCase(Locale.ROOT)))
                        .toList();
            }

            @Override
            public Sign fetchSign(SearchHit hit) {
                return Sign.builder(hit.uid(), hit.language(), hit.name())
                        .sourceUrl(hit.url())
                        .definition("Ville de Suisse alémanique")
                        .categories(List.of(Category.CITY))
                        .build();
            }

            @Override
            public void download(URI uri, Path target) {
                throw new ExtractionException("No media in the tests");
            }
        };
    }
}
