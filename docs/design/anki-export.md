# Anki export

## Why

Learners memorize signs with spaced repetition, and Anki is the reference tool for it (desktop, AnkiDroid, AnkiMobile).
Any list or search result can be exported as an Anki deck, so that learners revise the signs they chose, with the
SignSuisse video, in both directions: recognize a sign, and recall the sign of a word.

## Design

`AnkiExporter` (signaire-export, plain Java) writes an `.apkg` package: a zip with

- `collection.anki2`: an SQLite database in Anki's legacy schema, version 11 (tables and JSON configuration in
  `AnkiSchema`), written through `sqlite-jdbc` in a temporary file;
- the media, as entries named `0`, `1`, …, and `media`: a JSON map from these names to the file names.

A note per sign, with the fields of `AnkiExporter.FIELDS` (name, video, image, definition, periphrase, language,
categories, SignSuisse link, uid), labelled in the language of the export (`export*.properties`). Two card types:

- **sign → word**: the video on the front; only for signs with a video;
- **word → sign**: the name, language, categories and definition on the front, the video on the back.

Downloaded videos and images are included (`[sound:…]`, `<img>`); others are linked to SignSuisse. New cards are
shown in the order of the export.

The JSON of the collection (`col.conf`, `col.models`, `col.decks`, `col.dconf`) and the media map are written with
Jackson's tree model. `AnkiExporter` decides *what* goes in the deck (a `NoteType`: name, fields, card templates, the
field each card needs, CSS, built from the labels); `AnkiSchema` decides *how* Anki stores it (the JSON, the tables).

## Decisions

- **Legacy schema 11**: every Anki version imports it, current ones included (they convert it). Newer schemas
  (protobuf configuration, v18) would gain nothing for a deck that is only imported.
- **Stable identities**: notes have the guid `signaire-<uid>`, and the note type and deck ids derive from the
  language and the title (`stableId`). Importing an updated export of the same list updates the existing notes and
  keeps the learner's progress, instead of creating duplicates.
- **SHA-1 checksum of the sort field** (`csum`): required by the format to detect duplicates; not a security use.
- **Temporary file for the collection**: SQLite needs a file. `Files.createTempFile` makes it readable by its owner
  only, with a random name, and it is deleted after the export.
- **Inserts in batches**, in one transaction, with the parameters common to all rows (note type, deck, timestamp)
  set once.
- **Jackson for the JSON** instead of string concatenation: the earlier hand-written JSON needed its own escaping
  (`Markup.json`) and a method with nine parameters; Jackson (already used by the bundle, extractor and accounts
  modules) escapes everything. Its tree model rather than records: Anki's objects have many constant members, which
  records would turn into constructors with a dozen arguments. The constant deck configuration
  (`dconf`) stays a text block: it has no variable part.
- **No Anki library**: the known ones (genanki, …) are Python; the format needed for an import is small.

## Open questions

- Audio-only `[sound:]` is how Anki plays videos; AnkiMobile and AnkiDroid play them, but in a separate player on
  some versions.
