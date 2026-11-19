package fr.ledcom.signaire.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import fr.ledcom.signaire.extractor.UserAgent;

/** The User-Agent of a user who gave no contact, for the slices of the web layer. */
@TestConfiguration(proxyBeanMethods = false)
public class TestUserAgent {

    @Bean
    UserAgent userAgent() {
        return UserAgent.of(null, null);
    }
}
