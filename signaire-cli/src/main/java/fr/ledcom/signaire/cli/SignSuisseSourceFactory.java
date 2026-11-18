package fr.ledcom.signaire.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.extractor.SignSuisseClient;
import fr.ledcom.signaire.extractor.ThrottledHttpFetcher;

/** Extracts from the SignSuisse web site. */
@Component
public class SignSuisseSourceFactory implements SignSourceFactory {

    private static final int MAX_ATTEMPTS = 3;

    private final URI baseUri;

    /**
     * @param baseUri the address of SignSuisse; another one for tests
     */
    public SignSuisseSourceFactory(@Value("${signaire.signsuisse.url:https://www.sgb-fss.ch}") URI baseUri) {
        this.baseUri = baseUri;
    }

    @Override
    public SignSource create(Duration interval) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        return new SignSuisseClient(new ThrottledHttpFetcher(client, interval, MAX_ATTEMPTS), baseUri);
    }
}
