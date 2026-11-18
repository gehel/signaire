package fr.ledcom.signaire.accounts;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** A PostgreSQL container, shared by the tests that use the same Spring context. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    /** Keep in line with compose.yaml. */
    public static final String IMAGE = "postgres:18-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGE));
    }
}
