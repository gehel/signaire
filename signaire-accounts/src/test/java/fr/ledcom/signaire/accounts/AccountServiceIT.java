package fr.ledcom.signaire.accounts;

import org.springframework.context.annotation.Import;

/** On PostgreSQL, as servers run. */
@Import(PostgresTestConfiguration.class)
class AccountServiceIT extends AbstractAccountServiceTest {
}
