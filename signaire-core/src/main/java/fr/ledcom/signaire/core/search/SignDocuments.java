package fr.ledcom.signaire.core.search;

import java.io.IOException;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Sign;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The documents of the index: signs, and the records of the loaded bundles. Each document keeps its object as
 * JSON, and has a unique {@link #ID}.
 */
final class SignDocuments {

    static final String KIND = "kind";
    static final String SIGN = "sign";
    static final String BUNDLE = "bundle";
    static final String UID = "uid";
    static final String LANGUAGE = "language";
    static final String CATEGORIES = "categories";

    private static final String ID = "id";
    private static final String SOURCE = "source";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SignDocuments() {
        // static only
    }

    /** The term that identifies the document of a sign. */
    static Term signId(String uid) {
        return new Term(ID, id(SIGN, uid));
    }

    /** The term that identifies the record of a bundle. */
    static Term bundleId(String file) {
        return new Term(ID, id(BUNDLE, file));
    }

    static Document signDocument(Sign sign) {
        Document document = document(SIGN, sign.uid(), sign);
        document.add(new StringField(UID, sign.uid(), Field.Store.YES));
        document.add(new StringField(LANGUAGE, sign.language().name(), Field.Store.NO));
        for (Category category : sign.categories()) {
            document.add(new StringField(CATEGORIES, category.name(), Field.Store.NO));
        }
        addText(document, SignAnalyzers.NAME, sign.name());
        document.add(new TextField(SignAnalyzers.NAME_PREFIX, sign.name(), Field.Store.NO));
        document.add(new SortedDocValuesField(SignAnalyzers.NAME_SORT, SignAnalyzers.sortKey(sign.name())));
        addText(document, SignAnalyzers.PERIPHRASE, sign.periphrase());
        addText(document, SignAnalyzers.DEFINITION, sign.definition());
        for (Example example : sign.examples()) {
            addText(document, SignAnalyzers.EXAMPLES, example.text());
        }
        return document;
    }

    static Document bundleDocument(String file, Object entry) {
        return document(BUNDLE, file, entry);
    }

    static Sign readSign(StoredFields fields, int doc) throws IOException {
        return readSource(fields, doc, Sign.class);
    }

    /** The uid of the sign of a document. */
    static String readUid(StoredFields fields, int doc) throws IOException {
        return fields.document(doc, Set.of(UID)).get(UID);
    }

    /**
     * The object of a document.
     *
     * @throws SearchException when the object is not of this type
     */
    static <T> T readSource(StoredFields fields, int doc, Class<T> type) throws IOException {
        try {
            return JSON.readValue(fields.document(doc, Set.of(SOURCE)).getBinaryValue(SOURCE).bytes, type);
        } catch (JacksonException e) {
            throw new SearchException("Cannot read document " + doc + " as " + type.getSimpleName(), e);
        }
    }

    private static Document document(String kind, String key, Object source) {
        Document document = new Document();
        document.add(new StringField(ID, id(kind, key), Field.Store.NO));
        document.add(new StringField(KIND, kind, Field.Store.NO));
        try {
            document.add(new StoredField(SOURCE, JSON.writeValueAsBytes(source)));
        } catch (JacksonException e) {
            throw new SearchException("Cannot write " + source + " as JSON", e);
        }
        return document;
    }

    private static String id(String kind, String key) {
        return kind + "/" + key;
    }

    /** A text field, and its stemmed sub-fields. */
    private static void addText(Document document, String field, String text) {
        if (text == null) {
            return;
        }
        document.add(new TextField(field, text, Field.Store.NO));
        for (String language : SignAnalyzers.LANGUAGES) {
            document.add(new TextField(SignAnalyzers.stemmed(field, language), text, Field.Store.NO));
        }
    }
}
