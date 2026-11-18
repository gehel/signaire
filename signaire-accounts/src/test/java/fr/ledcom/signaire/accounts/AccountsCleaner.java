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
        jdbc.sql("TRUNCATE account, word_list, word_list_entry RESTART IDENTITY CASCADE").update();
    }
}
