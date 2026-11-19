package fr.ledcom.signaire.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.extractor.SignSuisseClient;
import fr.ledcom.signaire.extractor.ThrottledHttpFetcher;
import fr.ledcom.signaire.extractor.UserAgent;

/** Extracts from the SignSuisse web site. */
@Component
public class SignSuisseSourceFactory implements SignSourceFactory {

    private static final int MAX_ATTEMPTS = 3;

    private final URI baseUri;
    private final UserAgent userAgent;

    /**
     * @param baseUri the address of SignSuisse; another one for tests
     * @param contact the email address (or URL) of the user, sent to SignSuisse with every request
     * @param override a User-Agent replacing the one of Signaire
     */
    public SignSuisseSourceFactory(@Value("${signaire.signsuisse.url:https://www.sgb-fss.ch}") URI baseUri,
            @Value("${signaire.signsuisse.contact:}") String contact,
            @Value("${signaire.signsuisse.user-agent:}") String override) {
        this.baseUri = baseUri;
        this.userAgent = UserAgent.of(contact, override);
    }

    @Override
    public SignSource create(Duration interval) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        return new SignSuisseClient(new ThrottledHttpFetcher(client, interval, MAX_ATTEMPTS, userAgent), baseUri);
    }
}
