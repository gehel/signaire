package fr.ledcom.signaire.core.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.de.GermanLightStemFilter;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.fr.FrenchLightStemFilter;
import org.apache.lucene.analysis.it.ItalianLightStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.ElisionFilter;
import org.apache.lucene.util.BytesRef;

/**
 * How the text of signs is analyzed. Every text field has a folded version (lowercase, without accents nor
 * elided articles) and a stemmed version per language; the name also has a prefix version, for searches as you
 * type, and a sort key.
 *
 * <p>Change {@link SignIndex#SCHEMA} along with the analysis: the index is then rebuilt.
 */
final class SignAnalyzers {

    static final String NAME = "name";
    static final String NAME_PREFIX = "name.prefix";
    static final String NAME_SORT = "name.sort";
    static final String PERIPHRASE = "periphrase";
    static final String DEFINITION = "definition";
    static final String EXAMPLES = "examples.text";
    /** The free text fields; each one also has a stemmed sub-field per language. */
    static final List<String> TEXT_FIELDS = List.of(NAME, PERIPHRASE, DEFINITION, EXAMPLES);
    /** Suffixes of the stemmed sub-fields. */
    static final List<String> LANGUAGES = List.of("fr", "de", "it");

    private static final int MAX_PREFIX = 20;
    private static final CharArraySet ARTICLES = new CharArraySet(List.of("l", "m", "t", "qu", "n", "s", "j", "d",
            "c", "jusqu", "quoiqu", "lorsqu", "puisqu", "dell", "nell", "all", "sull", "dall", "un", "quest", "quell"),
            true);

    private static final Analyzer FOLDING = new ChainAnalyzer(in ->
            new ASCIIFoldingFilter(new LowerCaseFilter(new ElisionFilter(in, ARTICLES))));
    private static final Analyzer FOLDING_PREFIX = new ChainAnalyzer(in ->
            new EdgeNGramTokenFilter(new ASCIIFoldingFilter(new LowerCaseFilter(new ElisionFilter(in, ARTICLES))),
                    1, MAX_PREFIX, false));
    private static final Map<String, Analyzer> STEMMING = Map.of(
            "fr", new ChainAnalyzer(in -> new ASCIIFoldingFilter(
                    new FrenchLightStemFilter(new LowerCaseFilter(new ElisionFilter(in, ARTICLES))))),
            "de", new ChainAnalyzer(in -> new ASCIIFoldingFilter(
                    new GermanLightStemFilter(new GermanNormalizationFilter(new LowerCaseFilter(in))))),
            "it", new ChainAnalyzer(in -> new ASCIIFoldingFilter(
                    new ItalianLightStemFilter(new LowerCaseFilter(new ElisionFilter(in, ARTICLES))))));

    private static final Analyzer INDEXING = analyzer(FOLDING_PREFIX);
    private static final Analyzer SEARCHING = analyzer(FOLDING);

    private SignAnalyzers() {
        // static only
    }

    /** Analyzes the fields of the documents. */
    static Analyzer indexing() {
        return INDEXING;
    }

    /** Analyzes the text of searches: the prefixes of names are searched as whole words. */
    static Analyzer searching() {
        return SEARCHING;
    }

    /** The sort key of a name: lowercase, without accents. */
    static BytesRef sortKey(String name) {
        return FOLDING.normalize(NAME_SORT, name);
    }

    /** The stemmed sub-field of a field, in a language. */
    static String stemmed(String field, String language) {
        return field + "." + language;
    }

    private static Analyzer analyzer(Analyzer prefix) {
        Map<String, Analyzer> fields = new HashMap<>();
        fields.put(NAME_PREFIX, prefix);
        for (String field : TEXT_FIELDS) {
            fields.put(field, FOLDING);
            STEMMING.forEach((language, analyzer) -> fields.put(stemmed(field, language), analyzer));
        }
        return new PerFieldAnalyzerWrapper(FOLDING, fields);
    }

    /** The standard tokenizer, then filters. */
    private static final class ChainAnalyzer extends Analyzer {

        private final UnaryOperator<TokenStream> filters;

        ChainAnalyzer(UnaryOperator<TokenStream> filters) {
            this.filters = filters;
        }

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new StandardTokenizer();
            return new TokenStreamComponents(tokenizer, filters.apply(tokenizer));
        }

        /** For sort keys and the like: the filters that work on a whole value. */
        @Override
        protected TokenStream normalize(String fieldName, TokenStream in) {
            return new ASCIIFoldingFilter(new LowerCaseFilter(in));
        }
    }
}
