# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Signaire is a Swiss sign language (LSF / DSGS / LIS) learning and reference tool built on data from the
SGB-FSS **SignSuisse** lexicon (https://www.sgb-fss.ch/signsuisse/fr). Main features:

- search signs by name, synonym/periphrase, definition, example, category, language
- multi-user personal word lists (e.g. "words I'm learning", "words for context X")
- render any list or search result as an Anki deck, a printable PDF, an e-book, …
- UI in French first (web **and** CLI), then Italian, German, English

**Licensing constraint:** SignSuisse content is © Fédération Suisse des Sourds; reuse requires written
permission. Only the *code* is distributed. Every user runs the extractor themselves; never commit
extracted data, videos or images, and never add test fixtures copied verbatim from the site beyond the
minimal HTML/JSON snippets needed to test parsing.

## Architecture

Multi-module Maven build (`fr.ledcom:signaire`), Java 27, Spring Boot (imported as BOM, not as parent).

| Module | Role | Spring? |
|---|---|---|
| `signaire-model` | Domain types (sign, category, language, bundle manifest) | no |
| `signaire-bundle` | Read/write category bundles (zip) | no |
| `signaire-extractor` | Crawl SignSuisse and produce bundles | no |
| `signaire-core` | Bundle loading, Elasticsearch indexing/search, users and lists, exports | yes |
| `signaire-cli` | Spring Boot + picocli command line | yes |
| `signaire-web` | Spring MVC + Thymeleaf + htmx UI, and REST API for Android/sync | yes |
| `android/` (later) | Java Android app, Gradle build, offline-capable | — |

Key decisions:
- `signaire-model` and `signaire-bundle` must stay **Android-compatible**: no Spring, Java release 17
  (set per module), small dependency footprint. The Android app reuses them to read the same bundles
  offline.
- **Bundles**: one zip per SignSuisse category (`city.zip`…), containing `manifest.json` (format version,
  category, sign count, SHA-256 of every entry), `signs.json`, and `media/` (videos, photos, illustrations).
  A `Media` keeps the SignSuisse URL and, when downloaded, its path in the bundle. Bundles are produced by
  `signaire extraire`, loaded from a configured directory at startup and (re)loadable at runtime; loading
  is idempotent and keyed by SignSuisse `uid`.
- **Storage**: Elasticsearch holds only the sign corpus (fully rebuildable from bundles). User accounts
  and lists live in PostgreSQL (JPA). Lists reference signs by SignSuisse `uid`.
- **Auth**: local accounts (Spring Security form login, bcrypt) plus optional OIDC via configuration.
- **i18n**: French, German, Italian and English, always translated together: a change to a French
  bundle comes with the three translations (tests check that every translation has the keys of the
  French bundle). Never hardcode user-facing strings. The base bundles are French (`messages.properties`),
  others are `messages_<lang>.properties`. Web uses the Spring `MessageSource`; the language comes from
  `?lang=` (kept in a cookie), then `Accept-Language`, then French (`LocaleConfiguration`). The CLI uses
  one picocli resource bundle per command (`fr/ledcom/signaire/cli/<command>.properties`) and follows the
  JVM default locale (`LANG`, or `-Duser.language=de`); unsupported languages fall back to French.
  Commands and options stay French in every language (`extraire --sortie --categorie ville`); category
  names (`categories*.properties`) are shown in the user's language and accepted in any of them. Tests run
  in French (`argLine` in the root POM); switch with `DefaultLocale` in CLI tests. Build command lines
  with `SignaireCommand.commandLine(...)`.
- Printed exports (PDF, e-book) show the SignSuisse photo/illustration plus a QR code linking to the video.

### SignSuisse data source

- Search endpoint (JSON, capped at **100 results**, no paging):
  `https://www.sgb-fss.ch/signsuisse/index.php?eID=signsuisse_search&sword=<term>&lang=<fr|de|it|all>&category=<id>&curlang=fr`
  returns `{"items":[{"uid","name","kategorie","sprache","link"}],"count"}`. Matching is case and accent
  insensitive and **fuzzy** ("vaud" also finds "BAVARD"). An empty `sword` returns HTML, and `sword=0`
  answers HTTP 500. `SignEnumerator` copes with all of this: it refines truncated or failed queries
  character by character, and reports when it hit its query length limit.
- Category ids are in `Category`. `CategoryLabels` (extractor) maps the FR/DE/IT labels shown on pages.
- Detail pages are static HTML, reachable only through the `link` given by the search: the slug and the
  language path must match (`/signsuisse/lexikon/…` for German, `/signsuisse/fr/lexikon/…`,
  `/signsuisse/it/lexikon/…`). Each `uid` is one sign in one language: the same concept in DE and FR has
  different uids. Page labels are in the sign's language, so `SignPageParser` recognizes sections by the
  start of their FR/DE/IT label. German pages also have dialects and dialect variant videos.
- Plain HTTP + jsoup, throttled (`ThrottledHttpFetcher`, generic user agent). Parser test fixtures copy the
  page structure with invented text.

## Build and test

Use the Maven wrapper. The parent POM `org.wikimedia:wmf-jvm-parent-pom` enforces quality gates during
the build: checkstyle (validate phase), forbiddenapis (compile), spotbugs with findsecbugs and sb-contrib,
the no-package-cycles enforcer rule, duplicate-finder (verify), and sortpom (POM elements must be in
sorted order). CI (`.github/workflows/build.yml`) runs `./mvnw -B verify` on Temurin 27.

```bash
./mvnw verify                                   # full build with all quality checks
./mvnw -PnoVerify verify                        # skip the static analysis for a quick loop
./mvnw -pl signaire-web -am test                # one module and the modules it depends on
./mvnw -pl signaire-web -am test -Dtest=HomeControllerTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -pl signaire-web -am test -Dtest='HomeControllerTest#homePageIsInFrenchByDefault' -Dsurefire.failIfNoSpecifiedTests=false
./mvnw sortpom:sort                             # fix POM ordering after editing a pom.xml
```

Gotchas with Spring Boot and picocli:
- CLI subcommands are `@Scope("prototype")` beans: picocli doesn't reset every option (positional lists)
  between runs of the same object.
- Apps get the core services by importing `SignaireCoreConfiguration`, not by scanning `fr.ledcom.signaire`
  (that would pick up core's test application from the test-jar). It names the packages it scans as strings,
  because the no-package-cycles rule also covers test classes.

Gotchas with the WMF ruleset and Java 27:
- The root POM overrides the parent's `lombok`, `spotbugs`, `forbiddenapis` and `jacoco` versions because
  the parent's versions can't handle Java 27 class files. Check these first when bumping the JDK or the
  parent.
- Import order: `java.*`, `javax.*`, `org.*`, `com.*`, then everything else (`edu.*`, `picocli.*`, …) as
  one group; groups are separated by a blank line, static imports on top.
- Classes with only static methods need a non-public constructor (`HideUtilityClassConstructor`); Spring
  Boot application classes get a `protected` constructor.
- No trailing spaces, including in `.properties` files: write a significant trailing space as `\u0020`.
- Silence a spotbugs false positive with `@SuppressFBWarnings(value = ..., justification = ...)`
  (`spotbugs-annotations`, `provided` scope). Silence forbiddenapis with any annotation named
  `SuppressForbidden` (see the extractor tests). `ResourceBundle.getBundle` and `String.formatted` need an
  explicit locale.

Integration tests (`*IT.java`, run by failsafe in `verify`) use Testcontainers and need Docker. The
Elasticsearch container setup (`ElasticsearchTestConfiguration`) and `TestSigns` live in core's test-jar,
shared with the CLI (and web) tests. Run a single IT with
`./mvnw -pl signaire-core verify -Dit.test=SignSearchServiceIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`.

Running the CLI locally:

```bash
docker compose up -d                                     # Elasticsearch on localhost:9200
./mvnw -PnoVerify -pl signaire-cli -am package -DskipTests
java -jar signaire-cli/target/signaire-cli-1.0-SNAPSHOT-jar-with-dependencies.jar extraire -c religion -l fr -o bundles
java -jar signaire-cli/target/signaire-cli-1.0-SNAPSHOT-jar-with-dependencies.jar charger --bundles bundles
java -jar signaire-cli/target/signaire-cli-1.0-SNAPSHOT-jar-with-dependencies.jar chercher dieu
```

The `jar-with-dependencies` jar is self-contained (Spring Boot layout); CI publishes it as the
`signaire-cli` build artifact. The plain `signaire-cli-*.jar` holds only the module's classes.

Running the web UI locally (http://localhost:8080): it loads `signaire.bundles.directory` (default
`bundles`) in the background at startup, retrying while Elasticsearch starts, and serves the media from
the bundles.

```bash
./mvnw -PnoVerify -pl signaire-web -am package -DskipTests
java -jar signaire-web/target/signaire-web-1.0-SNAPSHOT.jar --signaire.bundles.directory=bundles
```

Extraction is slow on purpose (500 ms between requests), and media are large (~100 MB for the 76 French
signs of "religion"); use `--sans-medias` while developing. After changing the index mapping
(`signaire-signs.json`), reindex with `charger --reindexer`: the bundle registry only notices bundle
changes.

Web UI gotchas:
- Pages are Thymeleaf templates sharing `layout.html` fragments. htmx (webjar, version-agnostic path)
  requests get fragments only (`Htmx.isPartialRequest`), except history restoration; the language links
  are refreshed out of band (`hx-swap-oob`).
- Thymeleaf 3.1 forbids bean references (`@bean`) in link attributes: `Links` is a model attribute
  (`${links.withPage(2)}`, `${links.media(media)}`); exception handlers must add it themselves.
- Name `@PathVariable`/`@RequestParam` explicitly: the build doesn't compile with `-parameters`.
- Web test properties are in `src/test/resources/config/application.properties` (a second
  `application.properties` would trip duplicate-finder). Web ITs share one Spring context and one
  Elasticsearch container: keep their annotations identical.

## Conventions

- **Test first**: write the failing test before the implementation, JUnit 5 + AssertJ (no Hamcrest, no
  JUnit assertions).
- **Branch per feature** (`feature/<name>`), merged to `main` through a GitHub PR. Commits tell a story
  (one logical step each, buildable). History of an unmerged branch may be rewritten.
- Prefer records and immutable types; no Lombok (doesn't keep up with new JDKs).
