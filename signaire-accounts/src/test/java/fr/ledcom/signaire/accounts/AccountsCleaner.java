package fr.ledcom.signaire.accounts;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Empties the account tables between tests. */
@Component
public class AccountsCleaner {

    private final JdbcClient jdbc;

    public AccountsCleaner(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteAll() {
        // children first; DELETE works on every database
        jdbc.sql("DELETE FROM word_list_entry").update();
        jdbc.sql("DELETE FROM word_list").update();
        jdbc.sql("DELETE FROM sign_override").update();
        jdbc.sql("DELETE FROM sign_request").update();
        jdbc.sql("DELETE FROM account").update();
    }
}
