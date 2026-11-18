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
| `signaire-core` | Bundle loading, Elasticsearch indexing/search, media of the bundles | yes |
| `signaire-accounts` | PostgreSQL (JPA, Flyway): accounts, word lists, sign changes, sign requests | yes |
| `signaire-export` | PDF (PDFBox, ZXing QR codes), Anki decks (SQLite), EPUB books | no |
| `signaire-cli` | Spring Boot + picocli command line | yes |
| `signaire-web` | Spring MVC + Thymeleaf + htmx UI, and REST API for Android/sync | yes |
| `signaire-it` | Tests of the whole stack: CLI and web jars as processes | — |
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
  and lists live in PostgreSQL (`signaire-accounts`: JPA, schema by Flyway in `db/migration`, Hibernate
  only validates it). It's a separate module so that the CLI needs no database. Lists reference signs
  by SignSuisse `uid`. Entities and repositories are package-private; services expose records and take
  the id of the acting account, so a list of someone else is "not found".
- **Auth**: local accounts (Spring Security form login, bcrypt; `signaire.accounts.registration-enabled`)
  plus OpenID Connect when `spring.security.oauth2.client.registration.*` is configured (an account is
  created on the first login, keyed by issuer + subject). Controllers get the user with
  `@AuthenticationPrincipal AccountPrincipal` (`LocalUser` or `OidcAccountUser`). Searching and reading
  signs is public; `/lists/**` and `/signs/*/lists/**` need a login.
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
- **User directories**: `UserDirectories` (core) follows the XDG base directories, with the usual places on
  macOS and Windows. Spring applications get them through `UserDirectoriesEnvironment` (registered in
  `META-INF/spring.factories`): `signaire.bundles.directory` defaults to `$XDG_DATA_HOME/signaire/bundles`,
  `signaire.media.cache` to `$XDG_CACHE_HOME/signaire/media`, as Spring Boot default properties (any setting
  wins), and `$XDG_CONFIG_HOME/signaire/application.properties` is read. CLI options get these defaults
  through `EnvironmentDefaults`. Tests always set both directories under `target/`.
- **Signs are transient**: bundles and index can be deleted and rebuilt at any time. Everything else refers
  to signs by uid (SignSuisse uid, or the `local-…` uid of signs added by administrators), never by an id of
  the index; `TransientSignsIT` checks it. Keep it that way.
- **Administration** (`/admin/**`, role `ADMIN` = usernames in `signaire.accounts.admins`): changes to
  signs are stored in `sign_override` and applied again after every bundle load (`SignEditor.reapply()`,
  called by `StartupLoading.load()`, uploads and the sync); anything that loads bundles must call it.
  New signs get `local-…` uids. Bundles can be uploaded (checked, then named after their category), reloaded
  and reindexed. `SignSuisseSync` runs the extractor in a background virtual thread, one at a time,
  cancellable, against the `SignSource` bean (`ExtractionConfiguration`; web ITs replace it with
  `FakeSignSuisse`). Users request missing signs (`sign_request`); admins import them from SignSuisse
  (media linked, not downloaded), link an existing sign, create it, or reject the request.
- **Exports** (`signaire-export`, plain Java): the caller gives an `ExportRequest` (title, signs, locale)
  and a `MediaSource` reading downloaded media (web and CLI adapt `MediaLibrary`). Labels are in
  `export*.properties`. PDF: A4/A5/A6, 1–12 signs per page, sheets, booklet (half pages imposed in
  saddle-stitch order on landscape sheets, printed duplex short edge) or flashcards (backs mirrored for
  duplex long edge); each sign has its image and a QR code to its SignSuisse video. PDF text uses the
  standard Helvetica fonts (WinAnsi; other characters become `?`). Anki: legacy collection schema 11 (all
  versions import it), notes keyed by `signaire-<uid>`, stable model and deck ids so re-imports update.
  EPUB 3: a page per sign, videos linked or included.

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

Tests never use the network: the `argLine` of the root POM sends HTTP(S) to a closed local port, except for
localhost (Testcontainers), and `NoNetworkTest` checks it. Fake SignSuisse instead (`FakeSignSuisse` in web
ITs, a `SignSource` or a local `HttpServer` elsewhere); never point a test at sgb-fss.ch.

Integration tests (`*IT.java`, run by failsafe in `verify`) use Testcontainers and need Docker. The
Elasticsearch container setup (`ElasticsearchTestConfiguration`) and `TestSigns` live in core's test-jar,
shared with the CLI (and web) tests. Run a single IT with
`./mvnw -pl signaire-core verify -Dit.test=SignSearchServiceIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`.

`signaire-it` tests the whole stack as users run it: the CLI and web jars as processes (XDG directories under
`target/it`), Elasticsearch and PostgreSQL in containers, and a fake SignSuisse served locally
(`signaire.signsuisse.url`): extraction → bundles → web app → HTTP user journeys. Logs of the processes are in
`signaire-it/target/it/*.log`. Run it alone with `./mvnw -pl signaire-it -am verify -Dtest=NONE
-Dsurefire.failIfNoSpecifiedTests=false` (the other modules' ITs run too unless `-Dit.test=FullStackIT`).

Measures (`.github/workflows/quality.yml`): `ci/start-demo.sh` starts the web jar on bundles of invented signs
(`DemoBundles`, test code of `signaire-it`) with the development containers; Lighthouse CI asserts the
scores of `.lighthouserc.json` (accessibility must stay at 1), sitespeed.io the budget of
`.sitespeed/budget.json`. SonarCloud runs when the `SONAR_TOKEN` secret exists, and fails on its quality gate.

Running the CLI locally:

```bash
docker compose up -d                                     # Elasticsearch on localhost:9200
./mvnw -PnoVerify -pl signaire-cli -am package -DskipTests
java -jar signaire-cli/target/signaire-cli-1.0-SNAPSHOT-jar-with-dependencies.jar extraire -c religion -l fr -o bundles
java -jar signaire-cli/target/signaire-cli-1.0-SNAPSHOT-jar-with-dependencies.jar charger --bundles bundles
java -jar signaire-cli/target/signaire-cli-1.0-SNAPSHOT-jar-with-dependencies.jar chercher dieu
java -jar signaire-cli/target/signaire-cli-1.0-SNAPSHOT-jar-with-dependencies.jar exporter -f pdf -o dieu.pdf \
    --papier a5 --par-page 2 --mise-en-page livret -c religion
```

To look at a PDF while working on the layouts: `pdftoppm -r 50 -png -f 1 -l 1 file.pdf page`.

The `jar-with-dependencies` jar is self-contained (Spring Boot layout); CI publishes it as the
`signaire-cli` build artifact. The plain `signaire-cli-*.jar` holds only the module's classes.

Running the web UI locally (http://localhost:8080; `docker compose up -d` also starts PostgreSQL): it loads `signaire.bundles.directory` (default
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

To add an OpenID Connect provider, give the client and the issuer; providers unknown to Spring also
need the redirect URI:

```properties
spring.security.oauth2.client.registration.myidp.client-id=signaire
spring.security.oauth2.client.registration.myidp.client-secret=...
spring.security.oauth2.client.registration.myidp.client-name=My IdP
spring.security.oauth2.client.registration.myidp.scope=openid,profile,email
spring.security.oauth2.client.registration.myidp.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
spring.security.oauth2.client.provider.myidp.issuer-uri=https://idp.example.org
```

Help: the manual is Markdown, one page per topic and language in `signaire-web/src/main/resources/help/<lang>/`
(topics in `HelpPages.TOPICS`; a test checks that each exists in the four languages). Features link to their
topic with `~{fragments/help :: link('topic')}`. Update the manual with the feature it describes.

Web UI gotchas:
- Pages are Thymeleaf templates sharing `layout.html` fragments. htmx (webjar, version-agnostic path)
  requests get fragments only (`Htmx.isPartialRequest`), except history restoration; the language links
  are refreshed out of band (`hx-swap-oob`).
- Thymeleaf 3.1 forbids bean references (`@bean`) in link attributes: `Links` is a model attribute
  (`${links.withPage(2)}`, `${links.media(media)}`); exception handlers must add it themselves.
- Name `@PathVariable`/`@RequestParam` explicitly: the build doesn't compile with `-parameters`.
- The services are imported by `ServicesConfiguration`, not by the application class, so that
  `@WebMvcTest` slices (no Docker) don't load them; mock what the slice needs (`AccountService` for the
  security configuration).
- Forms posted with htmx (`hx-post` on a `<form th:action=…>`) carry the CSRF token as a hidden field.
- Messages rendered with arguments go through `MessageFormat`: write apostrophes `''` there only. Thymeleaf
  escapes `'` as `&#39;` in the output.
- Web test properties are in `src/test/resources/config/application.properties` (a second
  `application.properties` would trip duplicate-finder, so test-jars must not have one either). Web ITs
  are annotated `@WebIntegrationTest` only, to share one Spring context and the Elasticsearch and
  PostgreSQL containers.

Architecture rules: each module has an `ArchitectureTest` (ArchUnit) for what Maven dependencies can't say:
model and bundle stay Android compatible, extractor and export stay free of Spring, the packages of core don't
depend on each other (search ← bundles; media alone), accounts keeps its entities and repositories internal,
the CLI and web never reach Elasticsearch or JPA directly. Change the rules there when the architecture changes.

## Conventions

- **Test first**: write the failing test before the implementation, JUnit 5 + AssertJ (no Hamcrest, no
  JUnit assertions).
- **Branch per feature** (`feature/<name>`), merged to `main` through a GitHub PR. Commits tell a story
  (one logical step each, buildable). History of an unmerged branch may be rewritten.
- Prefer records and immutable types; no Lombok (doesn't keep up with new JDKs).
