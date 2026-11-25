package fr.ledcom.signaire.web;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.extractor.SignSuisseClient;
import fr.ledcom.signaire.extractor.ThrottledHttpFetcher;
import fr.ledcom.signaire.extractor.UserAgent;

/** SignSuisse, for synchronizing the bundles and importing requested signs. */
@Configuration(proxyBeanMethods = false)
public class ExtractionConfiguration {

    private static final int MAX_ATTEMPTS = 3;

    /**
     * @param interval minimum delay between two requests to SignSuisse, to stay polite
     * @param baseUri the address of SignSuisse; another one for tests
     */
    @Bean
    public SignSource signSuisse(@Value("${signaire.sync.interval:500ms}") Duration interval,
            @Value("${signaire.signsuisse.url:https://www.sgb-fss.ch}") URI baseUri, UserAgent userAgent) {
        return new SignSuisseClient(new ThrottledHttpFetcher(interval, MAX_ATTEMPTS, userAgent), baseUri);
    }

    /**
     * Says who sends the requests to SignSuisse, and how to reach them.
     *
     * @param contact the email address (or URL) of the user, sent to SignSuisse with every request
     * @param override a User-Agent replacing the one of Signaire
     */
    @Bean
    public UserAgent userAgent(@Value("${signaire.signsuisse.contact:}") String contact,
            @Value("${signaire.signsuisse.user-agent:}") String override) {
        return UserAgent.of(contact, override);
    }
}
