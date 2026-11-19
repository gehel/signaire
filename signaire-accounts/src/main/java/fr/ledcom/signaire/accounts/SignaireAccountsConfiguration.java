package fr.ledcom.signaire.accounts;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The account services; applications import this configuration. They run on PostgreSQL, or on H2 for the standalone
 * application: the migrations of {@code db/migration/common} are shared, those of {@code db/migration/<vendor>} are
 * specific. The scan skips what Spring Boot excludes, like {@code @SpringBootApplication}: in tests, the
 * {@code @TestConfiguration} classes of the package (a PostgreSQL container would replace the H2 database).
 */
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:fr/ledcom/signaire/accounts/accounts.properties")
@ComponentScan(basePackageClasses = SignaireAccountsConfiguration.class,
        excludeFilters = @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
@EntityScan(basePackageClasses = SignaireAccountsConfiguration.class)
@EnableJpaRepositories(basePackageClasses = SignaireAccountsConfiguration.class)
public class SignaireAccountsConfiguration {

    /** Hashes passwords with bcrypt, and tags them so that the algorithm can change later. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
