package fr.ledcom.signaire.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.extractor.SignSuisseClient;
import fr.ledcom.signaire.extractor.ThrottledHttpFetcher;

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
            @Value("${signaire.signsuisse.url:https://www.sgb-fss.ch}") URI baseUri) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        return new SignSuisseClient(new ThrottledHttpFetcher(client, interval, MAX_ATTEMPTS), baseUri);
    }
}
