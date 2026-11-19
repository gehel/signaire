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
  - `loaded()`: after bundles were loaded elsewhere (the SignSuisse synchronization loads each bundle as soon as it is
    extracted).

  Each refreshes the `MediaLibrary` (which serves the media of the bundles), then reapplies the changes of the
  administrators (`SignEditor.reapply()`).
- `AdminBundlesController`: the list of bundles, reload and reindex. `AdminUploadsController`: uploads (checked by
  `BundleUploads`, stored under the name of their category, then loaded).

## Decisions

- **One class for "load, then refresh and reapply"**: the rule "anything that loads bundles must reapply the changes"
  was repeated in three places (startup loading, uploads, synchronization); forgetting it silently undoes the work of
  administrators. `BundleLoading` owns it.
- **Renamed from `StartupLoading`**: it is no longer only about startup.
- **Uploads in their own controller**: the administration of bundles had grown to eight dependencies; uploads have
  their own (the upload store) and their own errors.
- **Loading failures are logged, not thrown, for the directory** (`load()`): one bad file must not keep the
  application from starting; the report lists each bundle's status. Uploads report errors to the administrator.

## Open questions

- The CLI (`charger`) loads with `BundleLoader` directly: it has no media library to refresh, and administrators'
  changes are a web feature. If the CLI ever needs them, the reapplication should move to core.
