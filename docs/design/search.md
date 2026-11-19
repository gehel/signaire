# Search

## Why

Learners look signs up by name, synonym or periphrase, words of the definition or examples, in a sign language and
category, with forgiving matching (accents, case, plurals, the start of a word). The search must work in the
standalone application on one computer, with nothing to install.

## Design

An embedded Lucene index (`signaire-core`, package `core.search`) in `signaire.index.directory`:

- `SignIndex` holds the sign documents and the registry of loaded bundles (`BundleRecord`: file, category,
  checksum, sign count, time of loading), so that unchanged bundles are skipped. It is entirely rebuildable from
  the bundles.
- `SignAnalyzers`: per field and language. Names are folded (case, accents, French elisions) and indexed with their
  prefixes (up to 20 characters) for search as you type; text fields get light stemmers for French, German and
  Italian; a sort field orders names.
- `SignSearchService`: a `SignQuery` (text, languages, categories, offset, size) becomes a Lucene query boosting
  exact names, then name prefixes, over periphrases, definitions and examples, with fuzzy matching of longer words
  for typos; results come with their total. Signs are found by uid too.
- `SignDocuments` maps signs to documents and back (the sign is stored as JSON in the document).
- Writes (`IndexWriters`) open a writer, commit and close it; a writer waits up to 30 s for the lock. The CLI and the
  web application can share the index, and searches see the commits of other processes.
- `SignIndex.SCHEMA` versions the fields and the analysis: an index of another schema is emptied when opened, and
  the bundles load again.

## Decisions

- **Lucene instead of Elasticsearch** (#24): Elasticsearch was the first choice (#8), but it's a server to install,
  run and secure, which the standalone application can't ask of its users, and tests needed a container. Lucene,
  embedded, gives the same analysis and queries (the former search tests passed unchanged).
- **Short writes instead of a long-lived writer**: lets `signaire charger` run while the web application runs; the
  cost (a commit per load) is small next to loading a bundle.
- **The index is a cache**: never the only copy of anything. Lists, changes and requests refer to signs by uid; the
  changes of administrators are reapplied after loading.
- **Schema version instead of migrations**: rebuilding from bundles is cheap and always correct.
- **Lucene stays in `core.search`** (ArchUnit): the CLI and the web application use `SignSearchService` and
  `SignIndex`, never Lucene types.

## Open questions

- Ranking is tuned by tests on invented signs; real-world feedback may call for other boosts or synonyms.
