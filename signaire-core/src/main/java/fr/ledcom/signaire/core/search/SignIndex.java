package fr.ledcom.signaire.core.search;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import fr.ledcom.signaire.model.Sign;

/**
 * The Elasticsearch indices of Signaire: the signs, and the registry of loaded bundles. Both can be rebuilt from
 * the bundles at any time.
 */
@Service
public class SignIndex {

    /** Index of the signs; documents are complete signs, keyed by uid. */
    public static final String SIGNS = "signaire-signs";
    /** Index of the bundles loaded into {@link #SIGNS}. */
    public static final String BUNDLES = "signaire-bundles";

    private static final int BULK_SIZE = 500;
    private static final String ALREADY_EXISTS = "resource_already_exists_exception";

    private final ElasticsearchClient client;

    public SignIndex(ElasticsearchClient client) {
        this.client = client;
    }

    /** Creates the indices that don't exist yet. */
    public void createIfMissing() {
        for (String index : List.of(SIGNS, BUNDLES)) {
            try {
                if (!client.indices().exists(e -> e.index(index)).value()) {
                    create(index);
                }
            } catch (IOException | ElasticsearchException e) {
                throw new SearchException("Cannot check index " + index, e);
            }
        }
    }

    /** Deletes and creates the indices, which empties them. */
    public void recreate() {
        for (String index : List.of(SIGNS, BUNDLES)) {
            try {
                client.indices().delete(d -> d.index(index).ignoreUnavailable(Boolean.TRUE));
            } catch (IOException | ElasticsearchException e) {
                throw new SearchException("Cannot delete index " + index, e);
            }
            create(index);
        }
    }

    /** Adds or replaces signs. */
    public void index(Collection<Sign> signs) {
        List<Sign> all = List.copyOf(signs);
        for (int start = 0; start < all.size(); start += BULK_SIZE) {
            indexBatch(all.subList(start, Math.min(start + BULK_SIZE, all.size())));
        }
    }

    private void indexBatch(List<Sign> batch) {
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (Sign sign : batch) {
            bulk.operations(op -> op.index(i -> i.index(SIGNS).id(sign.uid()).document(sign)));
        }
        BulkResponse response;
        try {
            response = client.bulk(bulk.build());
        } catch (IOException | ElasticsearchException e) {
            throw new SearchException("Cannot index signs", e);
        }
        if (response.errors()) {
            String reason = response.items().stream()
                    .filter(item -> item.error() != null)
                    .map(item -> item.id() + ": " + item.error().reason())
                    .findFirst().orElse("unknown");
            throw new SearchException("Cannot index signs, first error: " + reason);
        }
    }

    /** Removes a sign; nothing happens when it is not in the index. */
    public void delete(String uid) {
        try {
            client.delete(d -> d.index(SIGNS).id(uid));
        } catch (IOException | ElasticsearchException e) {
            throw new SearchException("Cannot delete sign " + uid, e);
        }
    }

    /** Makes the latest changes visible to searches. */
    public void refresh() {
        try {
            client.indices().refresh(r -> r.index(SIGNS, BUNDLES));
        } catch (IOException | ElasticsearchException e) {
            throw new SearchException("Cannot refresh indices", e);
        }
    }

    /** Creates an index; nothing happens when it already exists. */
    void create(String index) {
        try (InputStream definition = SignIndex.class.getResourceAsStream(index + ".json")) {
            if (definition == null) {
                throw new SearchException("No definition for index " + index);
            }
            client.indices().create(c -> c.index(index).withJson(definition));
        } catch (IOException e) {
            throw new SearchException("Cannot create index " + index, e);
        } catch (ElasticsearchException e) {
            // e.g. created between the check and the creation, while Elasticsearch recovers its indices
            if (!ALREADY_EXISTS.equals(e.error().type())) {
                throw new SearchException("Cannot create index " + index, e);
            }
        }
    }
}
