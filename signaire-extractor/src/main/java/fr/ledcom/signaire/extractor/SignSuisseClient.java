package fr.ledcom.signaire.extractor;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.URLEncoder;
import java.util.List;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

/** Access to the SignSuisse lexicon. */
public class SignSuisseClient implements SignSearch {

    /** SignSuisse returns at most this number of search results. */
    public static final int MAX_RESULTS = 100;
    public static final URI DEFAULT_BASE_URI = URI.create("https://www.sgb-fss.ch");

    private final HttpFetcher fetcher;
    private final URI baseUri;
    private final SearchResponseParser searchParser = new SearchResponseParser();
    private final SignPageParser pageParser = new SignPageParser();

    public SignSuisseClient(HttpFetcher fetcher, URI baseUri) {
        this.fetcher = fetcher;
        this.baseUri = baseUri;
    }

    @Override
    public List<SearchHit> search(String term, Language language, Category category) {
        URI uri = baseUri.resolve("/signsuisse/index.php?eID=signsuisse_search"
                + "&sword=" + encode(term)
                + "&lang=" + language.code()
                + "&category=" + category.signSuisseId()
                + "&curlang=fr");
        return searchParser.parse(fetcher.fetchText(uri));
    }

    public Sign fetchSign(SearchHit hit) {
        return pageParser.parse(fetcher.fetchText(URI.create(hit.url())), hit.url(), hit.uid(), hit.language());
    }

    /** Encodes like JavaScript's encodeURIComponent, which the SignSuisse page uses. */
    private static String encode(String term) {
        return URLEncoder.encode(term, UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~");
    }
}
