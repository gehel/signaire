package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

class SignSuisseClientTest {

    private static final String PAGE = "<html><body><div class=\"tx-is-signsuisse-lexikon\"><div class=\"showaction\">"
            + "<div class=\"contentleft\"><h1>LAUSANNE</h1></div></div></div></body></html>";

    private final RecordingFetcher fetcher = new RecordingFetcher();
    private final SignSuisseClient client = new SignSuisseClient(fetcher, URI.create("https://www.sgb-fss.ch"));

    @Test
    void searchesByTermLanguageAndCategory() {
        fetcher.response = "{\"items\":[{\"uid\":\"1\",\"name\":\"L'ABBAYE\",\"kategorie\":\"Ville\",\"sprache\":\"fr\","
                + "\"link\":\"https://www.sgb-fss.ch/signsuisse/fr/lexikon/1/labbaye\"}]}";

        List<SearchHit> hits = client.search("l'ab é", Language.FRENCH, Category.CITY);

        assertThat(hits).extracting(SearchHit::name).containsExactly("L'ABBAYE");
        assertThat(fetcher.requested).containsExactly(URI.create("https://www.sgb-fss.ch/signsuisse/index.php"
                + "?eID=signsuisse_search&sword=l'ab%20%C3%A9&lang=fr&category=6809&curlang=fr"));
    }

    @Test
    void fetchesSigns() {
        fetcher.response = PAGE;
        SearchHit hit = new SearchHit("1", "LAUSANNE", Language.FRENCH,
                "https://www.sgb-fss.ch/signsuisse/fr/lexikon/1/lausanne");

        Sign sign = client.fetchSign(hit);

        assertThat(sign.uid()).isEqualTo("1");
        assertThat(sign.name()).isEqualTo("LAUSANNE");
        assertThat(sign.sourceUrl()).isEqualTo(hit.url());
        assertThat(fetcher.requested).containsExactly(URI.create(hit.url()));
    }

    private static final class RecordingFetcher implements HttpFetcher {
        private final List<URI> requested = new ArrayList<>();
        private String response;

        @Override
        public String fetchText(URI uri) {
            requested.add(uri);
            return response;
        }

        @Override
        public void download(URI uri, Path target) {
            requested.add(uri);
        }
    }
}
