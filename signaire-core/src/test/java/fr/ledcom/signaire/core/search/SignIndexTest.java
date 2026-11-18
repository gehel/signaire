package fr.ledcom.signaire.core.search;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.core.FakeElasticsearch;
import fr.ledcom.signaire.core.TestSigns;

class SignIndexTest {

    @Test
    void indicesCreatedByElasticsearchMeanwhileAreFine() throws IOException {
        // while Elasticsearch recovers its indices, they can appear between the check and the creation
        try (FakeElasticsearch elasticsearch = FakeElasticsearch.failing(false, 400,
                "resource_already_exists_exception")) {
            SignIndex index = new SignIndex(elasticsearch.client());

            assertThatCode(index::createIfMissing).doesNotThrowAnyException();
        }
    }

    @Test
    void failuresToCreateIndicesAreReported() throws IOException {
        try (FakeElasticsearch elasticsearch = FakeElasticsearch.failing(false, 400, "illegal_argument_exception")) {
            SignIndex index = new SignIndex(elasticsearch.client());

            assertThatThrownBy(index::createIfMissing).isInstanceOf(SearchException.class)
                    .hasMessageContaining(SignIndex.SIGNS);
        }
    }

    @Test
    void errorsOfElasticsearchAreSearchExceptions() throws IOException {
        try (FakeElasticsearch elasticsearch = FakeElasticsearch.failing(true, 503, "cluster_block_exception")) {
            SignIndex index = new SignIndex(elasticsearch.client());

            assertThatThrownBy(index::recreate).isInstanceOf(SearchException.class);
            assertThatThrownBy(() -> index.index(List.of(TestSigns.CHAT))).isInstanceOf(SearchException.class);
            assertThatThrownBy(() -> index.delete(TestSigns.CHAT.uid())).isInstanceOf(SearchException.class);
            assertThatThrownBy(index::refresh).isInstanceOf(SearchException.class);
        }
    }
}
