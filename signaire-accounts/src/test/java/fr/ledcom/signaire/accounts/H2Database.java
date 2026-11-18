package fr.ledcom.signaire.accounts;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.test.context.TestPropertySource;

/** The account services on an H2 database in memory, in the mode of the standalone application. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:accounts;DB_CLOSE_DELAY=-1;" + H2Database.MODE,
    "spring.datasource.username=sa",
    "spring.datasource.password=",
})
public @interface H2Database {

    /** The settings of H2 for Signaire; keep in line with the standalone application. */
    String MODE = "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
}
