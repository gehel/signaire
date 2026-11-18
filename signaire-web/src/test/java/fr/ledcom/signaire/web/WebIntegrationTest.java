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

/**
 * The whole application with a PostgreSQL container, a Lucene index under {@code target/}, and a fake SignSuisse.
 * Integration tests use this annotation only, so that they share one Spring context and one container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, AccountsCleaner.class, FakeSignSuisse.class})
public @interface WebIntegrationTest {
}
