package fr.ledcom.signaire.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordListsTest {

    @TempDir
    Path tmp;

    @Test
    void listsAreKeptInTheirFile() {
        Path file = tmp.resolve("lists.json");
        WordLists lists = new WordLists(file);
        WordList nature = lists.create("Nature");
        lists.add(nature.id(), "4");
        lists.add(nature.id(), "5");
        lists.create("Villes");

        WordLists reopened = new WordLists(file);

        assertThat(reopened.lists()).extracting(WordList::name).containsExactly("Nature", "Villes");
        assertThat(reopened.list(nature.id())).hasValueSatisfying(
                list -> assertThat(list.signs()).containsExactly("4", "5"));
    }

    @Test
    void namesAreCheckedAsOnTheWeb() {
        WordLists lists = new WordLists(tmp.resolve("lists.json"));
        lists.create("Nature");

        assertThatThrownBy(() -> lists.create("  ")).isInstanceOf(WordListException.class)
                .extracting("reason").isEqualTo(WordListException.Reason.INVALID_NAME);
        assertThatThrownBy(() -> lists.create("x".repeat(WordLists.MAX_NAME_LENGTH + 1)))
                .extracting("reason").isEqualTo(WordListException.Reason.INVALID_NAME);
        assertThatThrownBy(() -> lists.create(" nature ")).extracting("reason")
                .isEqualTo(WordListException.Reason.NAME_TAKEN);
        assertThat(lists.create("  Villes ").name()).isEqualTo("Villes");
    }

    @Test
    void signsAreInAListOnceInTheOrderAdded() {
        WordLists lists = new WordLists(tmp.resolve("lists.json"));
        long id = lists.create("Nature").id();

        lists.add(id, "5");
        lists.add(id, "4");
        lists.add(id, "5");
        assertThat(lists.list(id).orElseThrow().signs()).containsExactly("5", "4");

        lists.remove(id, "5");
        assertThat(lists.list(id).orElseThrow().signs()).containsExactly("4");
    }

    @Test
    void listsAreRenamedAndDeleted() {
        WordLists lists = new WordLists(tmp.resolve("lists.json"));
        long nature = lists.create("Nature").id();
        long villes = lists.create("Villes").id();

        lists.rename(nature, "Paysages");
        lists.delete(villes);

        assertThat(lists.lists()).extracting(WordList::name).containsExactly("Paysages");
        assertThat(lists.listsWith("4")).isEmpty();
        assertThatThrownBy(() -> lists.add(villes, "4")).extracting("reason")
                .isEqualTo(WordListException.Reason.NOT_FOUND);
    }

    @Test
    void listsOfASign() {
        WordLists lists = new WordLists(tmp.resolve("lists.json"));
        long nature = lists.create("Nature").id();
        lists.create("Villes");
        lists.add(nature, "4");

        assertThat(lists.listsWith("4")).extracting(WordList::name).isEqualTo(List.of("Nature"));
    }
}
