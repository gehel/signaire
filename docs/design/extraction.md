# Extraction from SignSuisse

## Why

SignSuisse (SGB-FSS) is the reference lexicon of the Swiss sign languages (LSF, DSGS, LIS), but its content is
© Fédération Suisse des Sourds and can't be redistributed without written permission. Signaire distributes only code:
every user extracts the signs they need, for their own use, into bundles on their computer. The extractor must
therefore be usable by non-developers (CLI, web administration, standalone first run), **polite towards SignSuisse**
(as few requests as possible, spaced out), and **observable** (an extraction takes minutes to hours: users must see
where it is and what is left).

## Design

`signaire-extractor` (plain Java, no Spring) produces one bundle per category (see [Bundles](bundles.md)).

### Sources

- `SignSource` is the SignSuisse API as Signaire needs it: `search(term, language, category)`, `fetchSign(hit)`,
  `download(uri, target)`. `SignSuisseSource` implements it over `ThrottledHttpFetcher`; tests use fakes.
- `SignPageParser` reads the static detail pages (jsoup). Labels are in the sign's language, so sections are
  recognized by the start of their FR/DE/IT label; German pages also have dialects and dialect variant videos.
- `ThrottledHttpFetcher`: plain `java.net.http`. Request **starts** are at least 500 ms apart, whatever the number of
  threads; at most 2 requests are in flight (a long video download doesn't hold the searches and pages back); retries
  with exponential backoff on server errors (not on 4xx); downloads go through a temporary file.
- `UserAgent`: `Signaire (+https://github.com/gehel/signaire)`, with the user's contact when configured
  (`signaire.signsuisse.contact`), or a full override (`signaire.signsuisse.user-agent`).

### Enumeration: listing the signs of a category

The search endpoint is the only way to list signs. It returns at most 100 results, without paging, and matches
fuzzily (any name *containing* the term, plus near matches). Measured on SignSuisse (November 2026), its results are
**ordered**: the names that *start* with the term first, then the others, each part sorted alphabetically (accents and
case ignored, spaces before letters), and the cap cuts that list.

`SignEnumerator` lists names by **prefix**, using that order:

1. Search each initial character (`a`–`z`, `1`–`9`).
2. If fewer than 100 results come back, or some of them don't start with the prefix, every name starting with the
   prefix is known: this prefix is done.
3. Otherwise (100 results, all starting with the prefix), the names up to the last one are known, and the missing
   ones sort after it. With the last name as a cursor, the next character `c0` after the prefix in it gives the
   searches still needed: `prefix + c0` (the rest of that branch), and `prefix + c` for each character `c` that
   sorts after `c0`. Characters sorting before `c0` are complete. A space is never searched as a last character (the
   search ignores trailing spaces): the cursor is followed one character further instead.
4. Repeat until no search is pending, or a prefix reaches the length limit (reported as incomplete).

Every result is kept, including names that only contain the term: they are signs of the category too. Each new sign
is handed to the next stage as soon as it is found.

The previous enumeration refined *every* truncated query with *every* character of the alphabet; with fuzzy
substring matching, single and double characters are always truncated in large categories, so it sent thousands of
searches per category and language. The prefix enumeration only searches the branches left after the cursor. Measured
on SignSuisse: HOUSE_BUILDING in French, 355 signs in 35 searches; GENERAL in German, 2,199 signs in 132 searches;
samples of substring searches (`ung`, `sch`, `ä`, `ß`, `-`…) found no sign missed.

### Pipeline

`CategoryExtractor` runs three stages at the same time, in virtual threads, connected by queues:

1. **search**: the enumeration, language after language;
2. **signs**: each sign found is read from its detail page, or **reused** from the existing bundle of the category
   when it already has it (unless a refresh is asked: `--rafraichir`, or the box of the synchronization);
3. **media**: the chosen media (`MediaChoice`: all, videos, images, none) not in the media cache yet are downloaded;
   a media that fails stays linked to SignSuisse, the sign is kept.

When all three are done, the bundle is written. All requests go through the one `ThrottledHttpFetcher`, so running
the stages together doesn't raise the request rate above one request start every 500 ms: it only removes the idle
time (waiting for a download while no search runs, and the other way round).

### Observability

An `ExtractionTracker`, updated by the stages, gives an immutable `ExtractionStatus` at any time
(`CategoryExtractor.status()`):

- the stage of the category (searching, extracting, writing, done) and the language being searched;
- searches sent, pending (as best known: the count grows while names are found), failed; whether the enumeration
  is complete;
- signs found, read, reused, failed, and pending;
- media to download, downloaded, failed, pending, and bytes downloaded;
- what each stage is doing right now (the search term, the sign, the media file);
- elapsed time, and an estimate of the time left: the requests pending times the average time per request so far
  (a lower bound while the search runs).

`ExtractionListener` gets this status every few seconds and when the stage changes, and the failures (sign, media,
search) with their causes. The CLI prints a progress line; the synchronization page polls the status (htmx, every
2 s) and shows it, with the categories done and left; both log it at INFO every 30 s.

### Callers

`signaire extraire` (CLI), the SignSuisse synchronization of the web administration (see
[SignSuisse synchronization](signsuisse-sync.md)), and the import of a requested sign (one detail page, no
enumeration).

## Decisions

- **Every user extracts**: the licensing constraint. No extracted data, video or image in the repository, and test
  fixtures copy the structure of pages with invented text.
- **Prefix enumeration with a cursor, accepting rare misses**: it relies on the order of the results as observed.
  Names sorted differently by SignSuisse than by Signaire's assumptions (letters like `ß` or `œ`, punctuation sorting
  after letters) can be missed when their prefix has more than 100 names; names starting with `0` are not listed
  (SignSuisse fails on that search). In exchange, the number of searches drops by one to two orders of magnitude. The
  exhaustive substring refinement was rejected: thousands of requests per category for, in practice, the same
  signs.
- **Reuse signs already extracted**: a synchronization run again mostly finds the same signs; reading their pages
  again was most of its requests. A sign corrected on SignSuisse is only picked up with a refresh, which users can ask
  for.
- **Stages in parallel, one shared throttle**: parallelism removes idle time without increasing the load on
  SignSuisse; the throttle, not the number of threads, bounds the request rate. Two requests in flight at most, so
  that a large video doesn't block the rest.
- **Keep a sign whose media fails**: the sign is useful with its media linked; a failed media is reported, and the next
  extraction tries it again (it's not in the cache).
- **Status snapshots rather than events for progress**: the web page, the CLI and the logs read the same consistent
  state, computed in one place; events remain for failures, which need their cause.
- **The User-Agent names the project, and the user's address when given**: SignSuisse must be able to contact
  whoever generates load. Politeness wins over privacy; the help says so where the address is configured.
- **Media optional**: videos are large (~100 MB for the 76 French signs of "religion"). Without them a bundle
  references the SignSuisse URLs, which pages and exports link to.
- **No Spring, small dependencies**: the extractor stays plain Java and keeps the CLI start quick.
- **Tests never use the network**: the root POM sends HTTP(S) to a closed port; fakes stand for SignSuisse (the
  enumerator's fake orders its results like SignSuisse; the others have too few signs for the order to matter).

## Open questions

- If SignSuisse changes the order of its results, the enumeration silently finds fewer signs; the fakes encode the
  expected order, and a comparison with a substring sample (a few searches) would detect it.
- The status could also be exposed as JSON for monitoring tools.
