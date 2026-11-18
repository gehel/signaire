package fr.ledcom.signaire.accounts;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The account services; applications import this configuration. They run on PostgreSQL, or on H2 for the standalone
 * application: the migrations of {@code db/migration/common} are shared, those of {@code db/migration/<vendor>} are
 * specific.
 */
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:fr/ledcom/signaire/accounts/accounts.properties")
@ComponentScan(basePackageClasses = SignaireAccountsConfiguration.class)
@EntityScan(basePackageClasses = SignaireAccountsConfiguration.class)
@EnableJpaRepositories(basePackageClasses = SignaireAccountsConfiguration.class)
public class SignaireAccountsConfiguration {

    /** Hashes passwords with bcrypt, and tags them so that the algorithm can change later. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
