package fr.ledcom.signaire.extractor;

import java.util.ArrayList;
import java.util.List;

import fr.ledcom.signaire.model.Language;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Parses the JSON responses of the SignSuisse search. */
public class SearchResponseParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public List<SearchHit> parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new ExtractionException("Unexpected search response: " + abbreviate(json), e);
        }
        JsonNode items = root.path("items");
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode item : items) {
            hits.add(new SearchHit(
                    item.path("uid").asString(),
                    item.path("name").asString(),
                    Language.fromCode(item.path("sprache").asString()),
                    item.path("link").asString()));
        }
        return hits;
    }

    private static String abbreviate(String text) {
        return text.length() <= 100 ? text : text.substring(0, 100) + "…";
    }
}
