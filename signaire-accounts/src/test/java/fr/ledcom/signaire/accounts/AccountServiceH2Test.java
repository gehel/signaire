package fr.ledcom.signaire.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** On H2, as the standalone application runs. */
@H2Database
class AccountServiceH2Test extends AbstractAccountServiceTest {

    @Autowired
    private DataSource dataSource;

    /** The H2 tests share their Spring context: none of them may end up on PostgreSQL. */
    @Test
    void runsOnH2() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }
}
