package fr.ledcom.signaire.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import fr.ledcom.signaire.extractor.FakeSignSource;
import fr.ledcom.signaire.extractor.SignSource;

/** Replaces SignSuisse in the tests with the fake one, which serves the test signs. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeSignSuisseConfiguration {

    @Bean
    @Primary
    SignSource fakeSignSuisse() {
        return new FakeSignSource();
    }
}
