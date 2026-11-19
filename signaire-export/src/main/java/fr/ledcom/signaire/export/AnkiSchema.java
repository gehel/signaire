package fr.ledcom.signaire.export;

import java.util.ArrayList;
import java.util.List;

/** The tables and JSON configuration of an Anki collection, schema version 11. */
final class AnkiSchema {

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

    private AnkiSchema() {
    }

    static String conf(long modelId, long deckId) {
        return "{\"activeDecks\": [" + deckId + "], \"addToCur\": true, \"collapseTime\": 1200, \"curDeck\": "
                + deckId + ", \"curModel\": \"" + modelId + "\", \"dueCounts\": true, \"estTimes\": true, "
                + "\"newBury\": true, \"newSpread\": 0, \"nextPos\": 1, \"sortBackwards\": false, "
                + "\"sortType\": \"noteFld\", \"timeLim\": 0}";
    }

    static String decks(long deckId, String name, long now) {
        return "{\"1\": " + deck(1, "Default", now) + ", \"" + deckId + "\": " + deck(deckId, name, now) + "}";
    }

    private static String deck(long id, String name, long now) {
        return "{\"id\": " + id + ", \"name\": " + Markup.json(name) + ", \"mod\": " + now / 1000
                + ", \"usn\": -1, \"desc\": \"\", \"dyn\": 0, \"conf\": 1, \"collapsed\": false, "
                + "\"extendNew\": 10, \"extendRev\": 50, \"newToday\": [0, 0], \"revToday\": [0, 0], "
                + "\"lrnToday\": [0, 0], \"timeToday\": [0, 0]}";
    }

    /**
     * A note type.
     *
     * @param signToWordField index of the field a "sign to word" card needs
     * @param wordToSignField index of the field a "word to sign" card needs
     */
    static String model(long modelId, long deckId, String name, List<String> fields, long now,
            List<Template> templates, int signToWordField, int wordToSignField, String css) {
        List<String> fieldJson = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            fieldJson.add("{\"name\": " + Markup.json(fields.get(i)) + ", \"ord\": " + i + ", \"sticky\": false, "
                    + "\"rtl\": false, \"font\": \"Arial\", \"size\": 20, \"media\": []}");
        }
        List<String> templateJson = new ArrayList<>();
        for (int i = 0; i < templates.size(); i++) {
            Template template = templates.get(i);
            templateJson.add("{\"name\": " + Markup.json(template.name()) + ", \"ord\": " + i + ", \"qfmt\": "
                    + Markup.json(template.question()) + ", \"afmt\": " + Markup.json(template.answer())
                    + ", \"did\": null, \"bqfmt\": \"\", \"bafmt\": \"\"}");
        }
        return "{\"" + modelId + "\": {\"id\": " + modelId + ", \"name\": " + Markup.json(name)
                + ", \"type\": 0, \"mod\": " + now / 1000 + ", \"usn\": -1, \"sortf\": 0, \"did\": " + deckId
                + ", \"tmpls\": [" + String.join(", ", templateJson) + "], \"flds\": ["
                + String.join(", ", fieldJson) + "], \"css\": " + Markup.json(css)
                + ", \"latexPre\": " + Markup.json(LATEX_PREAMBLE)
                + ", \"latexPost\": " + Markup.json("\\end{document}")
                + ", \"req\": [[0, \"any\", [" + signToWordField + "]], [1, \"any\", [" + wordToSignField + "]]]"
                + ", \"tags\": [], \"vers\": []}}";
    }
}
