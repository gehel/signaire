package fr.ledcom.signaire.accounts;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Boots the account services for their tests, on H2 ({@link H2Database}). */
@SpringBootApplication
public class AccountsTestApplication {

    protected AccountsTestApplication() {
        // instantiated by Spring only
    }
}
