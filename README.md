# Signaire

Signaire is a tool to learn and look up signs of the Swiss sign languages (LSF, DSGS, LIS), built on the
[SignSuisse](https://www.sgb-fss.ch/signsuisse/fr) lexicon of the Swiss Federation of the Deaf (SGB-FSS).

- **Search** signs by name, periphrase, definition or example, in one or several sign languages and
  categories, and watch their videos.
- **Word lists**: each user keeps lists of signs ("signs I'm learning", "signs for work"…), filled from sign
  pages or from search results.
- **Exports** of a list or a search: printable PDF (A4, A5, A6, 1 to 12 signs per page, booklet to fold,
  double-sided flashcards; each sign has a QR code to its video), [Anki](https://apps.ankiweb.net) deck
  (sign → word and word → sign cards), EPUB e-book.
- **Administration**: add, edit and delete signs; upload and reload bundles; synchronize with SignSuisse;
  handle the signs requested by users.
- Web interface and command line in **French, German, Italian and English**.

## The data is not included

The content of SignSuisse (videos, images, texts) belongs to the SGB-FSS and may only be reused with its
written permission. Signaire distributes **code only**: each user extracts the data for their own use, with
`signaire extraire` or the synchronization page of the administration. Extracted data must not be shared.

## Quick start

You need Java 27 and Docker.

```bash
docker compose up -d                       # Elasticsearch and PostgreSQL for development
./mvnw -PnoVerify package -DskipTests      # build everything

# extract a category from SignSuisse into ~/.local/share/signaire/bundles (add --sans-medias for a quick try)
java -jar signaire-cli/target/signaire-cli-1.0-SNAPSHOT-jar-with-dependencies.jar extraire -c religion -l fr

# start the web application: it loads the bundles at startup
java -jar signaire-web/target/signaire-web-1.0-SNAPSHOT.jar --signaire.accounts.admins=myname
```

Then open <http://localhost:8080>, create an account named `myname` (it is an administrator), and search. The
manual is in the application ("Aide", `/help`); its pages are in `signaire-web/src/main/resources/help/`.

## Command line

```bash
alias signaire='java -jar signaire-cli/target/signaire-cli-1.0-SNAPSHOT-jar-with-dependencies.jar'
signaire --help
signaire extraire --categorie ville,nature --langue fr                     # SignSuisse → bundles
signaire charger                                                          # bundles → search index
signaire chercher lac --langue fr                                         # search
signaire exporter -f pdf -o lac.pdf --papier a5 --mise-en-page livret lac # export
```

The command line follows the language of the system (`LANG`, or `-Duser.language=de`).

## Configuration of the web application

Spring Boot properties, given on the command line (`--name=value`), in `~/.config/signaire/application.properties`
(read by the command line too), in an `application.properties` next to the jar, or as environment variables.
Signaire follows the [XDG base directories](https://specifications.freedesktop.org/basedir/latest/) on Linux
(`$XDG_DATA_HOME`, `$XDG_CONFIG_HOME`, `$XDG_CACHE_HOME`), and the usual directories on macOS
(`~/Library/…/Signaire`) and Windows (`%APPDATA%`, `%LOCALAPPDATA%`).

| Property | Default | |
|---|---|---|
| `signaire.bundles.directory` | `~/.local/share/signaire/bundles` | Bundles loaded at startup, and source of the media |
| `signaire.media.cache` | `~/.cache/signaire/media` | Media downloaded by extractions |
| `signaire.bundles.load-on-startup` | `true` | |
| `signaire.accounts.admins` | | Usernames of the administrators, comma separated |
| `signaire.accounts.registration-enabled` | `true` | Whether anyone can create an account |
| `signaire.sync.interval` | `500ms` | Delay between two requests to SignSuisse |
| `spring.elasticsearch.uris` | `http://localhost:9200` | |
| `spring.datasource.url`, `.username`, `.password` | local PostgreSQL of `compose.yaml` | |
| `spring.security.oauth2.client.*` | | OpenID Connect providers, see [CLAUDE.md](CLAUDE.md) |

## Architecture

A multi-module Maven build: `signaire-model` and `signaire-bundle` (signs and bundles, Android compatible),
`signaire-extractor` (SignSuisse), `signaire-core` (Elasticsearch search, bundle loading, media),
`signaire-accounts` (PostgreSQL: accounts, lists, changes, requests), `signaire-export` (PDF, Anki, EPUB),
`signaire-cli` and `signaire-web` (Spring MVC, Thymeleaf, htmx). Signs travel in **bundles**: one zip per
SignSuisse category with the signs and their media; the search index can always be rebuilt from them.

## Development

```bash
./mvnw verify            # build with all the tests (Docker needed) and quality checks
./mvnw -PnoVerify verify # without the static analysis
```

[CLAUDE.md](CLAUDE.md) documents the architecture, the conventions and the pitfalls of the build in detail;
[TODO.md](TODO.md) lists what comes next. Contributions follow the same rules: one branch per feature, tests
first (JUnit 5 and AssertJ), commits that tell a story.

## License

The license of the code has not been chosen yet.
