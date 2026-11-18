package fr.ledcom.signaire.core.search;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import fr.ledcom.signaire.model.Sign;

/** Searches signs in the {@link SignIndex#SIGNS} index. */
@Service
public class SignSearchService {

    /** The name, stemmed in each language: "chats" finds "CHAT". */
    private static final List<String> NAME_STEMMED_FIELDS = List.of("name.fr", "name.de", "name.it");
    /** Free text fields, with their language specific sub-fields. */
    private static final List<String> TEXT_FIELDS = List.of(
            "periphrase^2", "periphrase.fr", "periphrase.de", "periphrase.it",
            "definition", "definition.fr", "definition.de", "definition.it",
            "examples.text", "examples.text.fr", "examples.text.de", "examples.text.it");
    private static final SortOptions BY_NAME = SortOptions.of(s -> s.field(f -> f.field("name.sort").order(SortOrder.Asc)));
    private static final SortOptions BY_SCORE = SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));

    private final ElasticsearchClient client;

    public SignSearchService(ElasticsearchClient client) {
        this.client = client;
    }

    public SearchResults search(SignQuery query) {
        SearchResponse<Sign> response;
        try {
            response = client.search(s -> s
                    .index(SignIndex.SIGNS)
                    .query(query(query))
                    .from(query.from())
                    .size(query.size())
                    .trackTotalHits(t -> t.enabled(Boolean.TRUE))
                    .sort(sort(query)),
                    Sign.class);
        } catch (IOException e) {
            throw new SearchException("Cannot search signs", e);
        }
        long total = response.hits().total() == null ? 0 : response.hits().total().value();
        List<Sign> signs = response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
        return new SearchResults(total, signs);
    }

    /**
     * The uids of the signs matching a search, in the order of the results, ignoring the page of the query.
     *
     * @param max the maximum number of uids to return
     */
    public List<String> findUids(SignQuery query, int max) {
        SearchResponse<Void> response;
        try {
            response = client.search(s -> s
                    .index(SignIndex.SIGNS)
                    .query(query(query))
                    .size(max)
                    .source(source -> source.fetch(Boolean.FALSE))
                    .sort(sort(query)),
                    Void.class);
        } catch (IOException e) {
            throw new SearchException("Cannot search signs", e);
        }
        return response.hits().hits().stream().map(Hit::id).toList();
    }

    public Optional<Sign> findByUid(String uid) {
        try {
            GetResponse<Sign> response = client.get(g -> g.index(SignIndex.SIGNS).id(uid), Sign.class);
            return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
        } catch (IOException e) {
            throw new SearchException("Cannot get sign " + uid, e);
        }
    }

    /** The signs with the given uids, in the same order; unknown uids are skipped. */
    public List<Sign> findByUids(List<String> uids) {
        if (uids.isEmpty()) {
            return List.of();
        }
        try {
            MgetResponse<Sign> response = client.mget(m -> m.index(SignIndex.SIGNS).ids(uids), Sign.class);
            return response.docs().stream()
                    .filter(MultiGetResponseItem::isResult)
                    .map(item -> item.result().source())
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new SearchException("Cannot get signs " + uids, e);
        }
    }

    private static List<SortOptions> sort(SignQuery query) {
        return query.text() == null ? List.of(BY_NAME) : List.of(BY_SCORE, BY_NAME);
    }

    private static Query query(SignQuery query) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (!query.languages().isEmpty()) {
            bool.filter(f -> f.terms(t -> t.field("language").terms(v -> v.value(values(query.languages())))));
        }
        if (!query.categories().isEmpty()) {
            bool.filter(f -> f.terms(t -> t.field("categories").terms(v -> v.value(values(query.categories())))));
        }
        String text = query.text();
        if (text != null) {
            bool.should(s -> s.match(m -> m.field("name").query(text).boost(5f)));
            bool.should(s -> s.match(m -> m.field("name.prefix").query(text).operator(Operator.And).boost(2f)));
            bool.should(s -> s.multiMatch(m -> m.query(text).fields(NAME_STEMMED_FIELDS).operator(Operator.And)
                    .boost(3f)));
            // fuzzy, but with the right first letter: otherwise "laus" finds "Maus" and "eau"
            bool.should(s -> s.multiMatch(m -> m.query(text).fields(TEXT_FIELDS).operator(Operator.And)
                    .fuzziness("AUTO").prefixLength(1)));
            bool.minimumShouldMatch("1");
        }
        return Query.of(q -> q.bool(bool.build()));
    }

    private static List<FieldValue> values(Collection<? extends Enum<?>> values) {
        return values.stream().map(value -> FieldValue.of(value.name())).toList();
    }
}
