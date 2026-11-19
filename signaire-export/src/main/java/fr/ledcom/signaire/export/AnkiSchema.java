package fr.ledcom.signaire.export;

import java.util.List;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** The tables and JSON configuration of an Anki collection, schema version 11. */
final class AnkiSchema {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static final List<String> TABLES = List.of(
            "create table col (id integer primary key, crt integer not null, mod integer not null, "
                    + "scm integer not null, ver integer not null, dty integer not null, usn integer not null, "
                    + "ls integer not null, conf text not null, models text not null, decks text not null, "
                    + "dconf text not null, tags text not null)",
            "create table notes (id integer primary key, guid text not null, mid integer not null, "
                    + "mod integer not null, usn integer not null, tags text not null, flds text not null, "
                    + "sfld integer not null, csum integer not null, flags integer not null, data text not null)",
            "create table cards (id integer primary key, nid integer not null, did integer not null, "
                    + "ord integer not null, mod integer not null, usn integer not null, type integer not null, "
                    + "queue integer not null, due integer not null, ivl integer not null, factor integer not null, "
                    + "reps integer not null, lapses integer not null, left integer not null, odue integer not null, "
                    + "odid integer not null, flags integer not null, data text not null)",
            "create table revlog (id integer primary key, cid integer not null, usn integer not null, "
                    + "ease integer not null, ivl integer not null, lastIvl integer not null, "
                    + "factor integer not null, time integer not null, type integer not null)",
            "create table graves (usn integer not null, oid integer not null, type integer not null)",
            "create index ix_notes_usn on notes (usn)",
            "create index ix_cards_usn on cards (usn)",
            "create index ix_revlog_usn on revlog (usn)",
            "create index ix_cards_nid on cards (nid)",
            "create index ix_cards_sched on cards (did, queue, due)",
            "create index ix_revlog_cid on revlog (cid)",
            "create index ix_notes_csum on notes (csum)");

    static final String DECK_CONFIGURATIONS = """
            {"1": {"id": 1, "name": "Default", "mod": 0, "usn": 0, "maxTaken": 60, "autoplay": true, "timer": 0,
             "replayq": true, "dyn": false,
             "new": {"bury": true, "delays": [1, 10], "initialFactor": 2500, "ints": [1, 4, 7], "order": 1,
                     "perDay": 20, "separate": true},
             "lapse": {"delays": [10], "leechAction": 0, "leechFails": 8, "minInt": 1, "mult": 0},
             "rev": {"bury": true, "ease4": 1.3, "fuzz": 0.05, "ivlFct": 1, "maxIvl": 36500, "minSpace": 1,
                     "perDay": 100}}}""";

    /** Anki's default preamble of the LaTeX in notes. */
    private static final String LATEX_PREAMBLE = """
            \\documentclass[12pt]{article}
            \\special{papersize=3in,5in}
            \\usepackage[utf8]{inputenc}
            \\usepackage{amssymb,amsmath}
            \\pagestyle{empty}
            \\setlength{\\parindent}{0in}
            \\begin{document}
            """;

    /** A card template: its name, and its question and answer formats. */
    record Template(String name, String question, String answer) {
    }

    /**
     * What a note type holds: its fields, and its card templates (the first is "sign to word", the second
     * "word to sign").
     *
     * @param signToWordField index of the field a "sign to word" card needs
     * @param wordToSignField index of the field a "word to sign" card needs
     */
    record NoteType(String name, List<String> fields, List<Template> templates, int signToWordField,
            int wordToSignField, String css) {
        NoteType {
            fields = List.copyOf(fields);
            templates = List.copyOf(templates);
        }
    }

    private AnkiSchema() {
    }

    /** Collection settings: the deck and note type of the export are the current ones. */
    static String conf(long modelId, long deckId) {
        ObjectNode conf = JSON.createObjectNode();
        conf.putArray("activeDecks").add(deckId);
        conf.put("addToCur", true)
                .put("collapseTime", 1200)
                .put("curDeck", deckId)
                .put("curModel", String.valueOf(modelId))
                .put("dueCounts", true)
                .put("estTimes", true)
                .put("newBury", true)
                .put("newSpread", 0)
                .put("nextPos", 1)
                .put("sortBackwards", false)
                .put("sortType", "noteFld")
                .put("timeLim", 0);
        return JSON.writeValueAsString(conf);
    }

    /** The decks by id: Anki's default deck, and the deck of the export. */
    static String decks(long deckId, String name, long now) {
        ObjectNode decks = JSON.createObjectNode();
        decks.set("1", deck(1, "Default", now));
        decks.set(String.valueOf(deckId), deck(deckId, name, now));
        return JSON.writeValueAsString(decks);
    }

    private static ObjectNode deck(long id, String name, long now) {
        ObjectNode deck = JSON.createObjectNode()
                .put("id", id)
                .put("name", name)
                .put("mod", now / 1000)
                .put("usn", -1)
                .put("desc", "")
                .put("dyn", 0)
                .put("conf", 1)
                .put("collapsed", false)
                .put("extendNew", 10)
                .put("extendRev", 50);
        for (String counts : List.of("newToday", "revToday", "lrnToday", "timeToday")) {
            deck.putArray(counts).add(0).add(0);
        }
        return deck;
    }

    /** The note types by id: the one of the export. */
    static String models(long modelId, long deckId, NoteType type, long now) {
        ObjectNode model = JSON.createObjectNode()
                .put("id", modelId)
                .put("name", type.name())
                .put("type", 0)
                .put("mod", now / 1000)
                .put("usn", -1)
                .put("sortf", 0)
                .put("did", deckId);
        ArrayNode templates = model.putArray("tmpls");
        for (int i = 0; i < type.templates().size(); i++) {
            Template template = type.templates().get(i);
            templates.addObject()
                    .put("name", template.name())
                    .put("ord", i)
                    .put("qfmt", template.question())
                    .put("afmt", template.answer())
                    .putNull("did")
                    .put("bqfmt", "")
                    .put("bafmt", "");
        }
        ArrayNode fields = model.putArray("flds");
        for (int i = 0; i < type.fields().size(); i++) {
            fields.addObject()
                    .put("name", type.fields().get(i))
                    .put("ord", i)
                    .put("sticky", false)
                    .put("rtl", false)
                    .put("font", "Arial")
                    .put("size", 20)
                    .putArray("media");
        }
        model.put("css", type.css())
                .put("latexPre", LATEX_PREAMBLE)
                .put("latexPost", "\\end{document}");
        // the cards of each template are generated when this field is not empty
        ArrayNode required = model.putArray("req");
        required.addArray().add(0).add("any").addArray().add(type.signToWordField());
        required.addArray().add(1).add("any").addArray().add(type.wordToSignField());
        model.putArray("tags");
        model.putArray("vers");
        ObjectNode models = JSON.createObjectNode();
        models.set(String.valueOf(modelId), model);
        return JSON.writeValueAsString(models);
    }

    /** The media of a package: their entry names in the zip, to their file names. */
    static String media(List<String> fileNames) {
        ObjectNode media = JSON.createObjectNode();
        for (int i = 0; i < fileNames.size(); i++) {
            media.put(String.valueOf(i), fileNames.get(i));
        }
        return JSON.writeValueAsString(media);
    }
}
