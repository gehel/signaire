# Command line

## Why

Extraction, loading and exports are also useful without a web server: to prepare bundles for a server or a
standalone installation, to script exports, or to work in a terminal. The CLI is for French-speaking users first,
like the rest of Signaire.

## Design

`signaire-cli`: Spring Boot (no web server) and picocli, packaged as a self-contained `jar-with-dependencies`
(Spring Boot layout), published by CI.

- `SignaireCommand` with subcommands: `extraire` (extract categories into bundles), `charger` (load bundles into the
  index), `chercher` (search), `exporter` (PDF, Anki, EPUB of a search).
- Subcommands are `@Scope("prototype")` beans, created by a Spring-aware picocli factory; command lines are built with
  `SignaireCommand.commandLine(...)`.
- Options and their values (categories, languages, media, layouts) have converters and completion candidates
  (`CategoryNames`, `LanguageCodes`, `MediaNames`, `LayoutNames`): names are shown in the user's language and
  accepted in any of the four.
- Defaults of directories come from the user directories (`EnvironmentDefaults`, see
  [User directories](user-directories.md)), so `charger` and the web application share the index.
- Messages: one picocli resource bundle per command (`fr/ledcom/signaire/cli/<command>.properties`), following the
  JVM default locale.

## Decisions

- **Commands and options in French in every language** (`extraire --sortie --categorie ville`): scripts and
  documentation work everywhere; only messages and value names follow the user's language.
- **Spring Boot for the CLI**: the same core services and configuration (user directories, index) as the web
  application, without duplicating wiring. The CLI doesn't depend on accounts: no database.
- **Prototype-scoped commands**: picocli doesn't reset every option (positional lists) between runs of the same
  object.
- **Exit codes**: 0 on success, picocli's codes for usage errors, non-zero on failure; commands that can't fail
  partially are `Runnable`.

## Open questions

- No native image: the JVM start (about a second) is acceptable for these commands.
