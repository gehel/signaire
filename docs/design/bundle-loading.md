# Bundle loading

## Why

Signs come from bundles (one zip per SignSuisse category, see the architecture in `CLAUDE.md`), produced by every user
with the extractor, because SignSuisse content can't be redistributed. The web application must find the signs of
the bundles in its search index: at startup, after an administrator uploads a bundle, after a synchronization with
SignSuisse, and on demand (reload, reindex).

Administrators can change signs (`sign_override`); these changes must survive any loading, since signs are
transient: a bundle replaces the signs it contains.

## Design

- `BundleLoader` (core): loads a bundle file or a directory into the `SignIndex`, idempotently (keyed by uid; the
  registry of loaded bundles skips unchanged files).
- `BundleLoading` (web, formerly `StartupLoading`): the only entry point of the web application for loading, so that
  what must follow every loading happens in one place:
  - `load()`: the bundles of `signaire.bundles.directory` (at startup in a virtual thread, and the "reload" of the
    administration);
  - `load(Path bundle)`: one bundle (uploads);
  - `reindex()`: empty the index, then `load()`;
  - `indexExtracted(signs)`: signs being extracted by the SignSuisse synchronization, before their bundle exists: they
    are indexed with their media linked to SignSuisse (the media library serves only media of bundles), with the
    changes of administrators applied to them (an edited sign stays as edited, a deleted one isn't indexed);
  - `loaded()`: after bundles were loaded elsewhere.

  Except `indexExtracted`, each refreshes the `MediaLibrary` (which serves the media of the bundles), then reapplies
  the changes of the administrators (`SignEditor.reapply()`). The synchronization loads each bundle with
  `load(Path bundle)` as soon as it is written, which replaces the signs indexed while extracting with those of the
  bundle (their media downloaded).
- `AdminBundlesController`: the list of bundles, reload and reindex. `AdminUploadsController`: uploads (checked by
  `BundleUploads`, stored under the name of their category, then loaded).

## Decisions

- **One class for "load, then refresh and reapply"**: the rule "anything that loads bundles must reapply the changes"
  was repeated in three places (startup loading, uploads, synchronization); forgetting it silently undoes the work of
  administrators. `BundleLoading` owns it.
- **Signs searchable as soon as they are extracted**: a category can take half an hour; its signs appear one by one
  rather than all at the end. Their media stay on SignSuisse until the bundle is written: serving the media cache
  directly would make the cache a second source of media for the pages, for a few minutes per category.
- **Renamed from `StartupLoading`**: it is no longer only about startup.
- **Uploads in their own controller**: the administration of bundles had grown to eight dependencies; uploads have
  their own (the upload store) and their own errors.
- **Loading failures are logged, not thrown, for the directory** (`load()`): one bad file must not keep the
  application from starting; the report lists each bundle's status. Uploads report errors to the administrator.

## Open questions

- The CLI (`charger`) loads with `BundleLoader` directly: it has no media library to refresh, and administrators'
  changes are a web feature. If the CLI ever needs them, the reapplication should move to core.
