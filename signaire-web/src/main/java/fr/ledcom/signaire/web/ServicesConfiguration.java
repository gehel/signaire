package fr.ledcom.signaire.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import fr.ledcom.signaire.accounts.SignaireAccountsConfiguration;
import fr.ledcom.signaire.core.SignaireCoreConfiguration;

/**
 * The services of the application. Not on the application class, so that web layer tests
 * ({@code @WebMvcTest}) don't get them.
 */
@Configuration(proxyBeanMethods = false)
@Import({SignaireCoreConfiguration.class, SignaireAccountsConfiguration.class})
public class ServicesConfiguration {
}
