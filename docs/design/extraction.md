# Extraction from SignSuisse

## Why

SignSuisse (SGB-FSS) is the reference lexicon of the Swiss sign languages (LSF, DSGS, LIS), but its content is
© Fédération Suisse des Sourds and can't be redistributed without written permission. Signaire distributes only code:
every user extracts the signs they need, for their own use, into bundles on their computer. The extractor must
therefore be usable by non-developers (CLI, web administration, standalone first run) and polite towards SignSuisse.

## Design

`signaire-extractor` (plain Java, no Spring) produces one bundle per category (see [Bundles](bundles.md)):

- `SignSource` is the SignSuisse API as Signaire needs it: `search(term, language, category)`, `fetchSign(hit)`,
  `download(uri, target)`. `SignSuisseSource` implements it over `ThrottledHttpFetcher`; tests use fakes.
- `SignEnumerator` finds all the signs of a language and category through the search endpoint, which returns at
  most 100 results, has no paging, matches fuzzily and fails on some terms (`sword=0` answers HTTP 500, an empty
  term returns HTML). Queries start with single characters; a query that returns the maximum (or fails) is refined
  by appending each character of the alphabet, which grows with the characters found in names (spaces,
  apostrophes…). With substring matching this is exhaustive; a depth limit stops runaway refinement and the result
  says whether the enumeration was complete. It reports its progress (queries done, planned, signs found).
- `SignPageParser` reads the static detail pages (jsoup). Labels are in the sign's language, so sections are
  recognized by the start of their FR/DE/IT label; German pages also have dialects and dialect variant videos.
  `CategoryLabels` maps the page labels to `Category`.
- `CategoryExtractor` enumerates, fetches each sign, downloads the chosen media (`MediaChoice`: all, videos, images,
  none) into a media cache shared by all categories, and writes the bundle. A sign that fails is reported and the
  others are kept. An `ExtractionListener` follows the progress (searches, signs, media queued and downloaded).
- `ThrottledHttpFetcher`: plain `java.net.http`, at least 500 ms between requests, retries with exponential backoff
  on server errors (not on 4xx), downloads through a temporary file.
- `UserAgent`: `Signaire (+https://github.com/gehel/signaire)`, with the user's contact when configured
  (`signaire.signsuisse.contact`), or a full override (`signaire.signsuisse.user-agent`).

Callers: `signaire extraire` (CLI), the SignSuisse synchronization of the web administration (see
[SignSuisse synchronization](signsuisse-sync.md)), and the import of a requested sign.

## Decisions

- **Every user extracts**: the licensing constraint. No extracted data, video or image in the repository, and test
  fixtures copy the structure of pages with invented text.
- **Search endpoint plus detail pages, no crawling of the site**: the search is the only way to list signs, and
  detail pages are only reachable through the link it gives (slug and language path must match).
- **Slow on purpose**: 500 ms between requests, retries spaced out. An extraction of a category takes minutes; it's
  done once and resumed (cached media are not downloaded again).
- **The User-Agent names the project, and the user's address when given**: SignSuisse must be able to contact
  whoever generates load. Politeness wins over privacy; the help says so where the address is configured.
- **Media optional**: videos are large (~100 MB for the 76 French signs of "religion"). Without them a bundle
  references the SignSuisse URLs, which pages and exports link to.
- **No Spring, small dependencies**: the extractor may run elsewhere (it's plain Java), and keeps the CLI start
  quick.
- **Tests never use the network**: the root POM sends HTTP(S) to a closed port; fakes stand for SignSuisse.

## Open questions

- If SignSuisse changes its pages or search, the parser and enumerator need updating; the fixtures show which
  structure is expected.
