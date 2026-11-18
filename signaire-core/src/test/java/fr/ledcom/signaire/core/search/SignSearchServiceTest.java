package fr.ledcom.signaire.core.search;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.core.FakeElasticsearch;

class SignSearchServiceTest {

    @Test
    void errorsOfElasticsearchAreSearchExceptions() throws IOException {
        try (FakeElasticsearch elasticsearch = FakeElasticsearch.failing(true, 503, "cluster_block_exception")) {
            SignSearchService service = new SignSearchService(elasticsearch.client());
            SignQuery query = SignQuery.forText("chat");

            assertThatThrownBy(() -> service.search(query)).isInstanceOf(SearchException.class);
            assertThatThrownBy(() -> service.findUids(query, 10)).isInstanceOf(SearchException.class);
            assertThatThrownBy(() -> service.findByUid("3")).isInstanceOf(SearchException.class);
            assertThatThrownBy(() -> service.findByUids(List.of("3"))).isInstanceOf(SearchException.class);
        }
    }
}
