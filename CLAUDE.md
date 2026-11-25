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
| `signaire-core` | Bundle loading, Lucene indexing/search, media of the bundles | yes |
| `signaire-accounts` | PostgreSQL or H2 (JPA, Flyway): accounts, word lists, sign changes, sign requests | yes |
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
- **Search**: an embedded Lucene index (`SignIndex`, in `signaire.index.directory`) holds only the sign corpus
  and the registry of loaded bundles, fully rebuildable from bundles; no search server to run. Each write
  opens a writer, commits and closes it, so the CLI and the web application can share the index (a writer
  waits up to 30 s for the lock), and searches see the commits of other processes. The analysis
  (`SignAnalyzers`: folding, prefixes, light FR/DE/IT stemmers) is versioned by `SignIndex.SCHEMA`: bump it
  when fields or analysis change, an index of another schema is emptied on opening and the bundles reload.
  Lucene stays in `core.search` (ArchUnit).
- **Storage**: user accounts and lists live in PostgreSQL, or H2 for the standalone application (`signaire-accounts`:
  JPA, schema by Flyway, Hibernate only validates it). Migrations are in `db/migration/common`, except those that
  differ per database, in `db/migration/{postgresql,h2}` (`accounts.properties` sets the locations); a migration
  already applied never changes, even when moved. The service tests run on H2 (`@H2Database`, in the mode of the
  standalone application); PostgreSQL and its migrations are tested by `signaire-it`, whose web application runs
  on it. It's a separate module so that the CLI needs no database. Lists reference signs
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
  `signaire.media.cache` to `$XDG_CACHE_HOME/signaire/media`, `signaire.index.directory` to
  `$XDG_STATE_HOME/signaire/index`, as Spring Boot default properties (any setting
  wins), and `$XDG_CONFIG_HOME/signaire/application.properties` is read. CLI options get these defaults
  through `EnvironmentDefaults`. Tests always set these directories under `target/`.
- **Signs are transient**: bundles and index can be deleted and rebuilt at any time. Everything else refers
  to signs by uid (SignSuisse uid, or the `local-…` uid of signs added by administrators), never by an id of
  the index; `FullStackIT.everythingSurvivesTheDeletionAndExtractionOfAllTheSigns` checks it. Keep it that way.
- **Standalone application** (`StandaloneApplication` in web, the `-standalone.jar` of signaire-web): the web
  application with the `standalone` profile (`application-standalone.properties`: H2 file database in
  `signaire.state.directory`, `127.0.0.1:8180`, log file, `standalone.properties` of the config directory imported).
  When the port answers, it only opens the browser. `Standalone` (a bean of `SecurityConfiguration`, so that
  `@WebMvcTest` slices have it) knows the mode: `SetupFilter` sends to `/setup` until the first run is set up (alone,
  or several accounts with an administrator; signs to download through `SignSuisseSync`), `SingleUserFilter` makes
  every local request the single user (an account with a Signaire OIDC identity, administrator), and
  `/admin/quit` stops it (no console after a double click). Never on servers: `signaire.standalone.enabled` is false
  there. `ci/package-standalone.sh` runs jpackage (application image with its Java runtime) on the extracted jar, then
  a training run that writes the JVM's AOT cache (`signaire.aot`, in the launcher's options): the app starts in about
  3 s instead of 13 (the JVM ignores a cache whose jars changed). CI does it on the three systems. `StandaloneIT`
  (signaire-it) runs the jar: no container.
- **Administration** (`/admin/**`, role `ADMIN` = usernames in `signaire.accounts.admins`): changes to
  signs are stored in `sign_override` and applied again after every bundle load (`SignEditor.reapply()`):
  in the web application, anything that loads bundles goes through `BundleLoading`, which reapplies them (and
  refreshes the media) after each loading (`docs/design/bundle-loading.md`).
  New signs get `local-…` uids. Bundles can be uploaded (checked, then named after their category), reloaded
  and reindexed. `SignSuisseSync` runs the extractor in a background virtual thread, one at a time,
  cancellable, against the `SignSource` bean (`ExtractionConfiguration`; web tests replace it with
  `FakeSignSuisse`); each sign is indexed as soon as it is extracted, its bundle loaded at the end of its category. Users request missing signs (`sign_request`); admins import them from SignSuisse
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
  insensitive and **fuzzy** ("vaud" also finds "BAVARD"). Results are ordered: names *starting* with the term
  first, then the others, each sorted (accents ignored, spaces first), and the cap cuts that list. An empty `sword`
  returns HTML, and `sword=0` answers HTTP 500. `SignEnumerator` lists names by prefix, resuming after the last name
  of a truncated search (`docs/design/extraction.md`): a category of 2,000 signs takes about 130 searches.
- `CategoryExtractor` runs the search, the reading of signs (reused from the existing bundle unless refreshed) and the
  media downloads in parallel, under the one throttle; `ExtractionStatus` says where it is (CLI, sync page, logs).
- Category ids are in `Category`. `CategoryLabels` (extractor) maps the FR/DE/IT labels shown on pages.
- Detail pages are static HTML, reachable only through the `link` given by the search: the slug and the
  language path must match (`/signsuisse/lexikon/…` for German, `/signsuisse/fr/lexikon/…`,
  `/signsuisse/it/lexikon/…`). Each `uid` is one sign in one language: the same concept in DE and FR has
  different uids. Page labels are in the sign's language, so `SignPageParser` recognizes sections by the
  start of their FR/DE/IT label. German pages also have dialects and dialect variant videos.
- Plain HTTP + jsoup, throttled (`ThrottledHttpFetcher`). The `User-Agent` (`UserAgent`) names the project and, when
  the user gives one (`signaire.signsuisse.contact`), their email address: politeness towards SignSuisse wins over
  privacy, and the help says so where the address is configured. Parser test fixtures copy the
  page structure with invented text.

## Build and test

Use the Maven wrapper. The parent POM `org.wikimedia:wmf-jvm-parent-pom` enforces quality gates during
the build: checkstyle (validate phase), forbiddenapis (compile), spotbugs with findsecbugs and sb-contrib,
duplicate-finder (verify), and sortpom (POM elements must be in sorted order). Its rule against package cycles is
turned off (deprecated, and blind to Java 9+ class files): `PackageCyclesTest` (ArchUnit) checks them in each
module. CI (`.github/workflows/build.yml`) runs `./mvnw -B verify` on Temurin 27, without the build cache.

```bash
./mvnw verify                                   # full build with all quality checks; unchanged modules come from the cache
./mvnw verify -Dmaven.build.cache.enabled=false # really everything (CI does this)
./mvnw -PnoVerify verify                        # skip the static analysis for a quick loop
./mvnw -pl signaire-web -am test                # one module and the modules it depends on
./mvnw -pl signaire-web -am test -Dtest=HomeControllerTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -pl signaire-web -am test -Dtest='HomeControllerTest#homePageIsInFrenchByDefault' -Dsurefire.failIfNoSpecifiedTests=false
./mvnw sortpom:sort                             # fix POM ordering after editing a pom.xml
```

Build time (about 5 min for everything on 4 cores, two thirds of it tests, a quarter spotbugs; building modules in
parallel gains little: the build is CPU bound):
- The Maven build cache extension (`.mvn/extensions.xml`, `.mvn/maven-build-cache-config.xml`, cache in
  `~/.m2/build-cache`) skips the modules whose sources, POM and dependencies did not change, tests and static
  analysis included, and restores their jars, `classes` and `test-classes` into `target/`: after a change in web,
  only web and signaire-it build. It reruns tests when the selection changes (`-Dtest=…`, `-Dit.test=…`), and a
  build without tests never replaces one with tests. Builds that stop before `package` (`test`, `compile`) are not
  cached (`.mvn/maven.config`): a module restored without its jar would make the next modules compile against the
  stale snapshot of `~/.m2`. Files read by the build from outside `src/` and the POM are not inputs: if one matters,
  add it to the cache configuration.
- Test JVMs (`argLine`, and the jars run by `JavaProcess`) use the quick compiler and the serial collector: they
  start faster than the optimizing compiler pays back.

Gotchas with Spring Boot and picocli:
- CLI subcommands are `@Scope("prototype")` beans: picocli doesn't reset every option (positional lists)
  between runs of the same object.
- Apps get the core services by importing `SignaireCoreConfiguration`, not by scanning `fr.ledcom.signaire`
  (that would pick up test applications from test-jars). It names the packages it scans as strings,
  because `PackageCyclesTest` also covers test classes.

Gotchas with the WMF ruleset and Java 27:
- The root POM overrides the parent's `lombok`, `spotbugs`, `forbiddenapis` and `jacoco` versions because
  the parent's versions can't handle Java 27 class files. Check these first when bumping the JDK or the
  parent. It also overrides checkstyle: the parent's 10.16 rejects unnamed variables. The rules are the WMF ruleset
  from our copy, `checkstyle.xml`, because checkstyle 13.9 removed its `JavadocStyle` check: `SummaryJavadoc`
  replaces it, and a Javadoc of block tags alone stays allowed. Keep the copy in step with the parent's.
- Unused catch parameters and try-with-resources variables are `_`, except in the Java 17 modules (model,
  bundle), where `_` doesn't compile.
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
tests, a `SignSource` or a local `HttpServer` elsewhere); never point a test at sgb-fss.ch.

Only `signaire-it` needs Docker (PostgreSQL and Chromium in containers, Testcontainers); the other modules have unit
tests only. A test above the database mocks the services below it rather than using a database: the accounts tests
use H2, the web tests mock the account services (`@WebLayerTest`). The search needs no container: core tests open a
`SignIndex` on a `@TempDir`, Spring tests use `target/test-index`. `TestSigns` lives in core's test-jar, shared with
the CLI and web tests.

`signaire-it` tests the whole stack as users run it: the CLI and web jars as processes (XDG directories under
`target/it`, so both share the index), PostgreSQL in a container, and a fake SignSuisse served locally
(`signaire.signsuisse.url`): extraction → bundles → web app → HTTP user journeys. Logs of the processes are in
`signaire-it/target/it/*.log`. Run it alone with `./mvnw -pl signaire-it -am verify -Dtest=NONE
-Dsurefire.failIfNoSpecifiedTests=false`, and one of its tests with `-Dit.test=FullStackIT`.

`BrowserIT` opens the pages in Chromium (Selenium, `selenium/standalone-chromium` container reaching the web jar
through `Testcontainers.exposeHostPorts`), light and dark themes, computer and phone widths: it checks what the CSS
must get right (no page wider than a phone, cards, the tour) and writes screenshots of every page to
`signaire-it/target/screenshots/` (a CI artifact). Look at them after changing the look; use it, not a local
browser, to see pages. Its image tag follows the Selenium version of the Spring Boot BOM.

Measures (`.github/workflows/quality.yml`): `ci/start-demo.sh` starts the web jar on bundles of invented signs
(`DemoBundles`, test code of `signaire-it`) with the development containers; Lighthouse CI asserts the
scores of `.lighthouserc.json` (accessibility must stay at 1), sitespeed.io the budget of
`.sitespeed/budget.json` and, on pull requests, the measures of the last successful build of main: `ci/sitespeed.py`
summarizes them (main keeps them as the `sitespeed-baseline` artifact, 90 days) and fails when a timing, a size or the
requests of a page grow by more than 10% (and more than 100 ms, 1 KB or 1 request). The comparison is in the summary of
the job. SonarCloud runs when the `SONAR_TOKEN` secret exists, and fails on its quality gate. The gate wants 80% coverage of new
code, from the **unit tests only** (on purpose: the jacoco report of the parent POM is written before the
integration tests), so code reached only by ITs needs unit tests too. When a SonarCloud suggestion clashes with
the WMF checkstyle rules, checkstyle wins; a false positive gets a `// NOSONAR` with its reason.

Running the CLI locally:

```bash
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

Running the web UI locally (http://localhost:8080; `docker compose up -d` starts PostgreSQL): it loads `signaire.bundles.directory` (default
`bundles`) in the background at startup, and serves the media from the bundles.

```bash
./mvnw -PnoVerify -pl signaire-web -am package -DskipTests
java -jar signaire-web/target/signaire-web-1.0-SNAPSHOT.jar --signaire.bundles.directory=bundles
```

Extraction is slow on purpose (request starts 500 ms apart, at most 2 in flight), and media are large (~100 MB for the 76 French
signs of "religion"); use `--sans-medias` while developing. After changing the fields or the analysis of the
index, bump `SignIndex.SCHEMA`: the bundle registry only notices bundle changes, the schema version empties the
index so that everything is loaded again.

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
- The look is Pico CSS (webjar, red theme) then `static/css/signaire.css` (`docs/design/visual-design.md`). Pico sets
  `--pico-color` on links and buttons (use `--ink` for the color of text there), makes submit buttons as wide as
  their form and every `dialog` a full-screen modal (the tour overrides it); its light theme selector also matches in
  dark mode, so colors of ours have values in both blocks.
- The font, Atkinson Hyperlegible Next, is in `static/fonts` with its license (OFL); `layout.html` preloads it at the
  address the stylesheet uses (a webjar's links get a version in templates, not in CSS: it would load twice).
- The theme follows the system unless the user chose one (`?theme=dark`, cookie, `ThemeAdvice`): every page writes it
  as `<html th:lang="${#locale.language}" th:data-theme="${theme}">`. Our dark colors are in two blocks (dark system,
  dark chosen) that must stay the same: `BrowserIT` compares them.
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
- The guided tour (`TourController`): `?tour=N` renders step N as a `<dialog>` in the header of any page, pointing at
  the element marked `data-tour`; `static/js/tour.js` places it and dims the rest of the page. Without script, the dialog
  is a card under the header. Keep the `data-tour` markers when changing the pages.
- Messages rendered with arguments go through `MessageFormat`: write apostrophes `''` there only. Thymeleaf
  escapes `'` as `&#39;` in the output.
- Web test properties are in `src/test/resources/config/application.properties` (a second
  `application.properties` would trip duplicate-finder, so test-jars must not have one either).
- Web tests need no database: `@WebLayerTest(SomeController.class)` is a `@WebMvcTest` slice with the security
  configuration and the core services (Lucene under `target/test-index`), where the services of the accounts are
  mocks to stub and verify; `TestUsers` has logged-in users and an administrator. What needs the database too
  (signs are transient, user journeys) is tested in `signaire-it`.

Architecture rules: each module has an `ArchitectureTest` (ArchUnit) for what Maven dependencies can't say:
model and bundle stay Android compatible, extractor and export stay free of Spring, the packages of core don't
depend on each other (search ← bundles; media alone), accounts keeps its entities and repositories internal,
the CLI and web never reach Lucene or JPA directly. Change the rules there when the architecture changes. Its
`PackageCyclesTest` forbids cycles between packages, test classes included.

## Conventions

- **Design first**: a feature, or a change to the design of one, starts with its design document in `docs/design/`
  (why, design, decisions and rejected alternatives; template in `docs/design/README.md`), as the first commit of its
  branch. Keep it in step with the implementation.
- **Test first**: write the failing test before the implementation, JUnit 5 + AssertJ (no Hamcrest, no
  JUnit assertions).
- **Branch per feature** (`feature/<name>`), merged to `main` through a GitHub PR. Commits tell a story
  (one logical step each, buildable). History of an unmerged branch may be rewritten.
- Prefer records and immutable types; no Lombok (doesn't keep up with new JDKs).
- JavaDoc only where it adds what the code doesn't say (why, constraints, units, side effects): never a comment
  that restates the name of what it documents.
