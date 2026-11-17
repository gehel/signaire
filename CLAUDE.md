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
- **Bundles**: one zip per SignSuisse category, containing `manifest.json` (format version, category,
  languages, entry count, checksums), the sign metadata as JSON, and `media/` (videos `.mp4`, photos,
  illustrations). Bundles are loaded from a configured directory at startup, and can be (re)loaded at
  runtime; loading is idempotent and keyed by SignSuisse `uid`.
- **Storage**: Elasticsearch holds only the sign corpus (fully rebuildable from bundles). User accounts
  and lists live in PostgreSQL (JPA). Lists reference signs by SignSuisse `uid`.
- **Auth**: local accounts (Spring Security form login, bcrypt) plus optional OIDC via configuration.
- **i18n**: never hardcode user-facing strings. The base bundles are French (`messages.properties`);
  other languages get `messages_<lang>.properties`. Web uses the Spring `MessageSource`, and the supported
  locales are listed in `LocaleConfiguration` (add a locale there when adding a translation). CLI help
  texts use picocli resource bundles (`fr/ledcom/signaire/cli/messages.properties`).
- Printed exports (PDF, e-book) show the SignSuisse photo/illustration plus a QR code linking to the video.

### SignSuisse data source

- Search endpoint (JSON, capped at **100 results**):
  `https://www.sgb-fss.ch/signsuisse/index.php?eID=signsuisse_search&sword=<term>&lang=<fr|de|it|all>&category=<id>&curlang=fr`
  returns `{"items":[{"uid","name","kategorie","sprache","link"}],"count"}`. An empty `sword` returns
  HTML, not JSON. To enumerate exhaustively, query by prefix per category and refine the prefix when a
  query hits the cap.
- Category ids (the `category` filter): 6806 Nation, 6809 Ville, 6812 Circulation, 6815 Profession/École,
  6818 Famille, 6821 Sport/Temps libre, 6824 Nourriture, 6827 Ménage, 6830 Gens, 6833 Animaux,
  6836 Nature, 6839 Maison/Bâtiment, 6842 Politique/Droit, 6845 Médecine, 6848 Économie,
  6851 En général, 6854 Religion, 6857 Verbe, 6860 Propriété, 6863 Événements/Média.
- Detail pages are static HTML: `/signsuisse/{fr|it/}lexikon/{uid}/{slug}` (German has no language
  prefix). Each `uid` is one sign in one language: the same concept in DE and FR has different uids.
  The page has the main video(s) (`/signsuisse/fileadmin/signsuisse_ressources/videos/*.mp4`), the
  "Périphrase / Synonyme", "Définition", "Statut des signes", "Catégorie", an example sentence plus its
  example video, and optional photo/illustration/explanation/document blocks (`.photo_doc`,
  `.illustration_doc`, …).
- Use plain HTTP + jsoup (no headless browser). Throttle requests and identify with a generic user agent.

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
  (`spotbugs-annotations`, `provided` scope).

Integration tests (`*IT.java`, failsafe) will use Testcontainers for Elasticsearch and PostgreSQL, so
they need Docker.

## Conventions

- **Test first**: write the failing test before the implementation, JUnit 5 + AssertJ (no Hamcrest, no
  JUnit assertions).
- **Branch per feature** (`feature/<name>`), merged to `main` through a GitHub PR. Commits tell a story
  (one logical step each, buildable). History of an unmerged branch may be rewritten.
- Prefer records and immutable types; no Lombok (doesn't keep up with new JDKs).
