package fr.ledcom.signaire.core.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;
import org.springframework.stereotype.Service;

import fr.ledcom.signaire.model.Sign;

/** Searches the signs of the {@link SignIndex}. */
@Service
public class SignSearchService {

    private static final float NAME_BOOST = 5f;
    private static final float NAME_STEMMED_BOOST = 3f;
    private static final float NAME_PREFIX_BOOST = 2f;
    private static final float PERIPHRASE_BOOST = 2f;
    /** Below this length, a word must match exactly; up to the next one, with one edit; beyond, with two. */
    private static final int ONE_EDIT_LENGTH = 3;
    private static final int TWO_EDITS_LENGTH = 6;

    private static final Sort BY_NAME = new Sort(new SortField(SignAnalyzers.NAME_SORT, SortField.Type.STRING));
    private static final Sort BY_SCORE = new Sort(SortField.FIELD_SCORE,
            new SortField(SignAnalyzers.NAME_SORT, SortField.Type.STRING));
    private static final QueryBuilder EXACT = new QueryBuilder(SignAnalyzers.searching());
    /** Fuzzy, but with the right first letter: otherwise "laus" finds "Maus" and "eau". */
    private static final QueryBuilder FUZZY = new QueryBuilder(SignAnalyzers.searching()) {
        @Override
        protected Query newTermQuery(Term term, float boost) {
            int edits = maxEdits(term.text().codePointCount(0, term.text().length()));
            return boosted(edits == 0 ? new TermQuery(term) : new FuzzyQuery(term, edits, 1), boost);
        }
    };

    private final SignIndex index;

    public SignSearchService(SignIndex index) {
        this.index = index;
    }

    public SearchResults search(SignQuery query) {
        return index.read("Cannot search signs", searcher -> {
            Query lucene = query(query);
            int total = searcher.count(lucene);
            int wanted = Math.min(query.from() + query.size(), total);
            if (wanted <= query.from()) {
                return new SearchResults(total, List.of());
            }
            ScoreDoc[] docs = searcher.search(lucene, wanted, sort(query)).scoreDocs;
            StoredFields fields = searcher.storedFields();
            List<Sign> signs = new ArrayList<>();
            for (int i = query.from(); i < docs.length; i++) {
                signs.add(SignDocuments.readSign(fields, docs[i].doc));
            }
            return new SearchResults(total, signs);
        });
    }

    /**
     * The uids of the signs matching a search, in the order of the results, ignoring the page of the query.
     *
     * @param max the maximum number of uids to return
     */
    public List<String> findUids(SignQuery query, int max) {
        if (max <= 0) {
            return List.of();
        }
        return index.read("Cannot search signs", searcher -> {
            StoredFields fields = searcher.storedFields();
            List<String> uids = new ArrayList<>();
            for (ScoreDoc doc : searcher.search(query(query), max, sort(query)).scoreDocs) {
                uids.add(SignDocuments.readUid(fields, doc.doc));
            }
            return uids;
        });
    }

    public Optional<Sign> findByUid(String uid) {
        return findByUids(List.of(uid)).stream().findFirst();
    }

    /** The signs with the given uids, in the same order; unknown uids are skipped. */
    public List<Sign> findByUids(List<String> uids) {
        if (uids.isEmpty()) {
            return List.of();
        }
        return index.read("Cannot get signs " + uids, searcher -> {
            Map<String, Sign> found = new HashMap<>();
            StoredFields fields = searcher.storedFields();
            Query query = signs(new TermInSetQuery(SignDocuments.UID, uids.stream().map(BytesRef::new).toList()));
            for (ScoreDoc doc : searcher.search(query, uids.size()).scoreDocs) {
                Sign sign = SignDocuments.readSign(fields, doc.doc);
                found.put(sign.uid(), sign);
            }
            return uids.stream().map(found::get).filter(Objects::nonNull).toList();
        });
    }

    /** The "AUTO" fuzziness of Elasticsearch. */
    private static int maxEdits(int length) {
        if (length < ONE_EDIT_LENGTH) {
            return 0;
        }
        return length < TWO_EDITS_LENGTH ? 1 : 2;
    }

    private static Sort sort(SignQuery query) {
        return query.text() == null ? BY_NAME : BY_SCORE;
    }

    private static Query query(SignQuery query) {
        BooleanQuery.Builder bool = new BooleanQuery.Builder();
        bool.add(new TermQuery(new Term(SignDocuments.KIND, SignDocuments.SIGN)), Occur.FILTER);
        if (!query.languages().isEmpty()) {
            bool.add(terms(SignDocuments.LANGUAGE, query.languages()), Occur.FILTER);
        }
        if (!query.categories().isEmpty()) {
            bool.add(terms(SignDocuments.CATEGORIES, query.categories()), Occur.FILTER);
        }
        String text = query.text();
        if (text == null) {
            // the filters alone
            return bool.build();
        }
        should(bool, boosted(EXACT.createBooleanQuery(SignAnalyzers.NAME, text), NAME_BOOST));
        should(bool, boosted(EXACT.createBooleanQuery(SignAnalyzers.NAME_PREFIX, text, Occur.MUST),
                NAME_PREFIX_BOOST));
        should(bool, boosted(bestField(EXACT, text, stemmed(SignAnalyzers.NAME), Map.of()), NAME_STEMMED_BOOST));
        List<String> textFields = new ArrayList<>();
        for (String field : List.of(SignAnalyzers.PERIPHRASE, SignAnalyzers.DEFINITION, SignAnalyzers.EXAMPLES)) {
            textFields.add(field);
            textFields.addAll(stemmed(field));
        }
        should(bool, bestField(FUZZY, text, textFields, Map.of(SignAnalyzers.PERIPHRASE, PERIPHRASE_BOOST)));
        bool.setMinimumNumberShouldMatch(1);
        return bool.build();
    }

    /** Signs only, not the records of bundles. */
    private static Query signs(Query query) {
        return new BooleanQuery.Builder()
                .add(query, Occur.MUST)
                .add(new TermQuery(new Term(SignDocuments.KIND, SignDocuments.SIGN)), Occur.FILTER)
                .build();
    }

    /** All the words in one of the fields; the score is the best of the fields. */
    private static Query bestField(QueryBuilder builder, String text, Iterable<String> fields,
            Map<String, Float> boosts) {
        List<Query> perField = new ArrayList<>();
        for (String field : fields) {
            Query query = builder.createBooleanQuery(field, text, Occur.MUST);
            if (query != null) {
                perField.add(boosted(query, boosts.getOrDefault(field, 1f)));
            }
        }
        return perField.isEmpty() ? null : new DisjunctionMaxQuery(perField, 0f);
    }

    private static List<String> stemmed(String field) {
        return SignAnalyzers.LANGUAGES.stream().map(language -> SignAnalyzers.stemmed(field, language)).toList();
    }

    /** Adds a clause, unless the text has no word for it. */
    private static void should(BooleanQuery.Builder bool, Query query) {
        if (query != null) {
            bool.add(query, Occur.SHOULD);
        }
    }

    private static Query boosted(Query query, float boost) {
        return query == null || boost == 1f ? query : new BoostQuery(query, boost);
    }

    private static Query terms(String field, Collection<? extends Enum<?>> values) {
        return new TermInSetQuery(field, values.stream().map(value -> new BytesRef(value.name())).toList());
    }
}
