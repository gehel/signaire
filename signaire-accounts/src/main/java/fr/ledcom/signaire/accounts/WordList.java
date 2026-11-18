package fr.ledcom.signaire.accounts;

import java.util.List;

/**
 * A word list of an account.
 *
 * @param id identifier of the list
 * @param name name of the list, unique for its owner (case insensitive)
 * @param signUids SignSuisse uids of the signs of the list, in the order they were added
 */
public record WordList(long id, String name, List<String> signUids) {

    public WordList {
        signUids = List.copyOf(signUids);
    }

    public int size() {
        return signUids.size();
    }
}
