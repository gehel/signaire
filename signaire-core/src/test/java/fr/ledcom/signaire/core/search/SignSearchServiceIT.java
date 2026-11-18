package fr.ledcom.signaire.core.search;

import static fr.ledcom.signaire.core.TestSigns.GATTO;
import static fr.ledcom.signaire.core.TestSigns.KATZE;
import static fr.ledcom.signaire.core.TestSigns.LAUSANNE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import fr.ledcom.signaire.core.CoreTestApplication;
import fr.ledcom.signaire.core.ElasticsearchTestConfiguration;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

@SpringBootTest(classes = CoreTestApplication.class)
@Import(ElasticsearchTestConfiguration.class)
class SignSearchServiceIT {

    @Autowired
    private SignIndex index;

    @Autowired
    private SignSearchService search;

    @BeforeEach
    void indexSigns() {
        index.recreate();
        index.index(TestSigns.ALL);
        index.refresh();
    }

    @Test
    void findsSignsByName() {
        assertThat(names(SignQuery.forText("lausanne"))).first().isEqualTo("LAUSANNE");
    }

    @Test
    void ignoresAccentsAndCase() {
        assertThat(names(SignQuery.forText("geneve"))).containsExactly("GENÈVE");
    }

    @Test
    void findsSignsByTheInflectedFormsOfTheirName() {
        assertThat(names(SignQuery.forText("chats"))).first().isEqualTo("CHAT");
        assertThat(names(SignQuery.forText("Katzen"))).first().isEqualTo("KATZE");
    }

    @Test
    void findsSignsByTheBeginningOfTheirName() {
        assertThat(names(SignQuery.forText("laus"))).containsExactly("LAUSANNE");
    }

    @Test
    void findsSignsByPeriphraseAndDefinition() {
        assertThat(names(SignQuery.forText("jet d'eau"))).containsExactly("GENÈVE");
        assertThat(names(SignQuery.forText("canton de vaud"))).first().isEqualTo("LAUSANNE");
    }

    @Test
    void findsSignsByExamples() {
        assertThat(names(SignQuery.forText("travaille"))).containsExactly("LAUSANNE");
    }

    @Test
    void appliesLanguageStemming() {
        assertThat(names(SignQuery.forText("félins"))).contains("CHAT");
        assertThat(names(SignQuery.forText("Haustiere"))).contains("KATZE");
    }

    @Test
    void filtersByLanguageAndCategory() {
        SignQuery animalsInGerman = new SignQuery(null, Set.of(Language.GERMAN), Set.of(Category.ANIMALS), 0, 10);
        SignQuery frenchCities = new SignQuery(null, Set.of(Language.FRENCH), Set.of(Category.CITY), 0, 10);

        assertThat(names(animalsInGerman)).containsExactly("KATZE");
        assertThat(names(frenchCities)).containsExactly("GENÈVE", "LAUSANNE");
    }

    @Test
    void listsAllSignsSortedByNameWithoutText() {
        SearchResults results = search.search(SignQuery.all(0, 10));

        assertThat(results.total()).isEqualTo(5);
        assertThat(results.signs()).extracting(Sign::name)
                .containsExactly("CHAT", "GATTO", "GENÈVE", "KATZE", "LAUSANNE");
    }

    @Test
    void pagesThroughResults() {
        SearchResults results = search.search(SignQuery.all(2, 2));

        assertThat(results.total()).isEqualTo(5);
        assertThat(results.signs()).extracting(Sign::name).containsExactly("GENÈVE", "KATZE");
    }

    @Test
    void returnsCompleteSigns() {
        assertThat(search.search(SignQuery.forText("lausanne")).signs()).first().isEqualTo(LAUSANNE);
        assertThat(search.search(SignQuery.forText("katze")).signs()).containsExactly(KATZE);
    }

    @Test
    void findsSignsByUid() {
        assertThat(search.findByUid(GATTO.uid())).contains(GATTO);
        assertThat(search.findByUid("unknown")).isEmpty();
    }

    @Test
    void unknownWordsFindNothing() {
        assertThat(names(SignQuery.forText("xylophone"))).isEmpty();
    }

    private List<String> names(SignQuery query) {
        return search.search(query).signs().stream().map(Sign::name).toList();
    }
}
