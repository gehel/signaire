package fr.ledcom.signaire.offline;

import java.util.List;

/**
 * A word list of the phone.
 *
 * @param signs the uids of its signs, in the order they were added (signs are transient: a list outlives them)
 */
public record WordList(long id, String name, List<String> signs) {

    public WordList {
        signs = List.copyOf(signs);
    }
}
