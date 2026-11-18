package fr.ledcom.signaire.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** What only the standalone application does ({@link StandaloneApplication}). */
@Configuration(proxyBeanMethods = false)
public class StandaloneConfiguration {

    @Bean
    @ConditionalOnProperty(name = "signaire.standalone.open-browser", havingValue = "true")
    public Browser browser() {
        return new Browser();
    }
}
