package fr.ledcom.signaire.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import fr.ledcom.signaire.accounts.AccountsCleaner;
import fr.ledcom.signaire.accounts.PostgresTestConfiguration;
import fr.ledcom.signaire.core.ElasticsearchTestConfiguration;

/**
 * The whole application with Elasticsearch and PostgreSQL containers, and a fake SignSuisse. Integration tests use this annotation
 * only, so that they share one Spring context and one set of containers.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({ElasticsearchTestConfiguration.class, PostgresTestConfiguration.class, AccountsCleaner.class,
    FakeSignSuisse.class})
public @interface WebIntegrationTest {
}
