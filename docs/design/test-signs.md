# Test signs and the fake SignSuisse

## Why

Tests invent signs in many places, each in its own way:

- `TestSigns` (core's test-jar): five signs that the core, CLI and web tests index and search;
- `DemoBundles` (`signaire-it`): twenty signs for the screenshots of `BrowserIT` and `AndroidIT`, the demonstration
  and the measures of CI (Lighthouse, sitespeed.io);
- six fakes of SignSuisse, each with signs of its own: an HTTP server in `signaire-it` (LAUSANNE and GENÈVE, 9001 and
  9002), and `SignSource`s in the tests of the extractor, of `signaire-offline`, of the CLI and of the web (two there:
  ZOUG and ZURICH, AARAU).

LAUSANNE has another uid, other texts and other media in each. A reader learns every fake; a change to `SignSource`
or to the pages of SignSuisse is made six times; and nothing checks that the pages the HTTP fake writes by hand are
still read by the parser as it reads those of SignSuisse.

Licensing doesn't change: the signs are invented, nothing comes from SignSuisse.

## Design

- **One set of invented signs, in `signaire-model`'s test-jar**: `TestSigns`, moved down from core. Model is the
  module every other one uses, the Android-compatible ones included (its tests are Java 17, as its code). About twenty
  signs, in three categories (Ville, Animaux, Nature) and the three sign languages, so that a category has signs in
  each language and the demonstration has enough to show. The signs the tests name keep their texts (LAUSANNE, GENÈVE,
  CHAT, KATZE, GATTO, uids 1 to 5): the search tests of core are written on them, and index them alone
  (`TestSigns.FIVE`), their totals and orders being those of these five; `TestSigns.LEXICON` has them all. Between
  them, the signs have what a page of SignSuisse can have: periphrase, definition, status, examples (one with its
  video), video, photo, illustration, and a German sign with dialects and a dialect variant.
  - As a bundle has them: media with their address, shaped as SignSuisse's
    (`/signsuisse/fileadmin/signsuisse_ressources/…`) on `example.org`, and, for some, their file in the bundle (the
    tests of media need one).
  - As SignSuisse has them (`TestSigns.atSignSuisse`): the same, without files; that is what the fakes serve.
- **The fake SignSuisse, in `signaire-extractor`'s test-jar**, serving `TestSigns`, in two forms that share the
  search and the hooks:
  - `FakeSignSource`, a `SignSource` in the process, for the tests of the code above the client: the extractor,
    `signaire-offline`, the CLI and the web. Its search is SignSuisse's: by category and language, accents and case
    ignored, names starting with the term first. `fetchSign` returns the sign as SignSuisse has it; `download` writes
    invented bytes. Hooks: a page or a medium that fails, a search that waits until released (cancellation, stages
    running together); the searches, pages and downloads it served; `reset()` for a fake that tests share (a Spring
    bean).
  - `FakeSignSuisse`, the same over HTTP (`com.sun.net.httpserver`, on `127.0.0.1`), for what runs the real client:
    the CLI and web jars in `signaire-it`, the Android app, `SignSuisseClient`'s tests. The search JSON, the pages
    (the structure of SignSuisse's, as the parser's fixtures have it, with the signs' texts), the media (invented
    bytes, a PNG for images). A test reads every page it serves with `SignPageParser` and finds its sign again: the
    fake keeps the structure of SignSuisse as long as the parser's fixtures do.
  - The web's tests get `FakeSignSource` as the `SignSource` bean (`FakeSignSuisseConfiguration`, a
    `@TestConfiguration`).
- **The demonstration** (`DemoBundles`) writes `TestSigns` as bundles, one per category, with an image of a color of
  its own per sign, generated, and no video, as now. The pages that CI measures and that `BrowserIT` and `AndroidIT`
  show are those of these signs. `AndroidIT`'s phone starts without the city, which it downloads from the fake
  SignSuisse: an extraction reuses the signs of the bundle already there, so a city on the phone would be kept as the
  demonstration has it.
- **Unchanged**: the names that `SignEnumeratorTest` generates (the cap of 100 results needs hundreds of names), the
  server of `ThrottledHttpFetcherTest` (statuses, retries, timing: HTTP itself), and the parser's fixtures (the
  structure of the real pages, which the fake follows).

## Decisions

- **The test-jar of `signaire-model`** rather than a module of test fixtures: every module already uses model, and a
  module for a dozen classes would be one more to build and to keep Android compatible.
- **Two forms over the same signs** rather than HTTP only: the tests of extraction stay free of a server and of the
  parser, and their hooks (a failing page, a paused search) are plain code; what runs the real client gets the real
  protocol.
  - HTTP only, every test through `SignSuisseClient`: one fake, but each unit test also tests the parser and HTTP.
- **Pages written from the signs**, rather than files: the signs are the data, and the test reading the pages back
  keeps the writing honest.
- **The pages measured by CI change** (`/?q=arbre`, `/signs/90000` become pages of the new signs): sitespeed.io
  shows them as new pages once, then compares them with main's as before.

## Open questions

- The fake's videos aren't videos: the Android app's player can't play them, so `AndroidIT` checks that videos were
  downloaded without playing them. A small MP4, invented, would let it play one; the build has no encoder to make it.
