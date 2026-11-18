package fr.ledcom.signaire.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Hibernate checks that the entities match the schema made by Flyway
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
abstract class AbstractWordListServiceTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private WordListService lists;

    @Autowired
    private AccountsCleaner cleaner;

    private long alice;
    private long bob;

    @BeforeEach
    void createAccounts() {
        cleaner.deleteAll();
        alice = accounts.register("alice", "correct horse", null).id();
        bob = accounts.register("bob", "correct horse", null).id();
    }

    @Test
    void createsListsOfSigns() {
        WordList list = lists.create(alice, "  À apprendre ");
        lists.add(alice, list.id(), "124593");
        lists.add(alice, list.id(), "120674");

        WordList found = lists.get(alice, list.id());

        assertThat(found.name()).isEqualTo("À apprendre");
        assertThat(found.signUids()).containsExactly("124593", "120674");
    }

    @Test
    void addingASignTwiceKeepsOneEntry() {
        WordList list = lists.create(alice, "Famille");

        assertThat(lists.add(alice, list.id(), "1")).isTrue();
        assertThat(lists.add(alice, list.id(), "1")).isFalse();

        assertThat(lists.get(alice, list.id()).signUids()).containsExactly("1");
    }

    @Test
    void addsSeveralSignsAtOnce() {
        WordList list = lists.create(alice, "Famille");
        lists.add(alice, list.id(), "2");

        int added = lists.addAll(alice, list.id(), List.of("1", "2", "3", "1"));

        assertThat(added).isEqualTo(2);
        assertThat(lists.get(alice, list.id()).signUids()).containsExactly("2", "1", "3");
        long id = list.id();
        List<String> more = List.of("4");
        assertThatThrownBy(() -> lists.addAll(bob, id, more)).isInstanceOf(WordListException.class);
    }

    @Test
    void removesSigns() {
        WordList list = lists.create(alice, "Famille");
        lists.add(alice, list.id(), "1");
        lists.add(alice, list.id(), "2");

        lists.remove(alice, list.id(), "1");

        assertThat(lists.get(alice, list.id()).signUids()).containsExactly("2");
    }

    @Test
    void listsTheListsOfAnAccountByName() {
        lists.create(alice, "Travail");
        WordList family = lists.create(alice, "famille");
        lists.add(alice, family.id(), "1");
        lists.create(bob, "Sport");

        assertThat(lists.lists(alice)).extracting(WordList::name, WordList::size)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("famille", 1),
                        org.assertj.core.groups.Tuple.tuple("Travail", 0));
    }

    @Test
    void namesAreUniquePerAccount() {
        lists.create(alice, "Famille");
        lists.create(bob, "Famille");

        assertThatThrownBy(() -> lists.create(alice, "famille"))
                .isInstanceOfSatisfying(WordListException.class,
                        e -> assertThat(e.reason()).isEqualTo(WordListException.Reason.NAME_TAKEN));
    }

    @Test
    void rejectsBlankOrLongNames() {
        for (String name : new String[] {" ", "x".repeat(101)}) {
            assertThatThrownBy(() -> lists.create(alice, name))
                    .isInstanceOfSatisfying(WordListException.class,
                            e -> assertThat(e.reason()).isEqualTo(WordListException.Reason.INVALID_NAME));
        }
    }

    @Test
    void renamesAndDeletesLists() {
        WordList list = lists.create(alice, "Famille");
        lists.add(alice, list.id(), "1");

        lists.rename(alice, list.id(), "Ma famille");
        assertThat(lists.get(alice, list.id()).name()).isEqualTo("Ma famille");

        lists.delete(alice, list.id());
        assertThat(lists.lists(alice)).isEmpty();
    }

    @Test
    void listsAreVisibleToTheirOwnerOnly() {
        long id = lists.create(alice, "Famille").id();

        assertThatThrownBy(() -> lists.get(bob, id)).isInstanceOfSatisfying(WordListException.class,
                e -> assertThat(e.reason()).isEqualTo(WordListException.Reason.NOT_FOUND));
        assertThatThrownBy(() -> lists.add(bob, id, "1")).isInstanceOf(WordListException.class);
        assertThatThrownBy(() -> lists.delete(bob, id)).isInstanceOf(WordListException.class);
        assertThat(lists.get(alice, id).signUids()).isEmpty();
    }

    @Test
    void findsTheListsContainingASign() {
        WordList family = lists.create(alice, "Famille");
        lists.create(alice, "Travail");
        lists.add(alice, family.id(), "1");

        assertThat(lists.listsContaining(alice, "1")).containsExactly(family.id());
        assertThat(lists.listsContaining(bob, "1")).isEmpty();
    }
}
