# Bundles

## Why

Extracted signs need a portable form: written by the extractor, read by the web application, the CLI and later the
Android app (offline), copied between computers or uploaded by an administrator. A bundle is that form: one file per
SignSuisse category, self-contained and verifiable.

## Design

A bundle is a zip, named after its category (`city.zip`, `sport-leisure.zip`), holding:

- `manifest.json`: format version, category, sign count, creation time, and the SHA-256 of every other entry;
- `signs.json`: the signs (`signaire-model`: uid, language, name, categories, definition, periphrase, examples,
  videos, images, dialects, dialect variants, SignSuisse URL);
- `media/`: the downloaded videos and images.

A `Media` keeps the SignSuisse URL and, when downloaded, its path in the bundle; readers fall back to the URL.

`signaire-bundle` has `BundleWriter` (writes to a temporary file next to the target, then moves it into place
atomically; refuses media paths that leave the media root) and `BundleReader` (rejects unknown format versions,
checks the hash of `signs.json`). `Bundle.verifyMedia()` checks the hashes of the media too: uploads do it, loading
doesn't (it would read every video at each startup). JSON goes through Jackson (`BundleJson`).

## Decisions

- **One bundle per category**: categories are how SignSuisse is organized and how users choose what to download;
  a category can be extracted again or replaced without touching the others.
- **Zip + JSON**: readable everywhere (Android included) with no database, and inspectable by hand.
- **Hashes in the manifest**: an upload is verified entirely before it is stored; a corrupted or tampered bundle is
  rejected instead of half-loaded.
- **Android compatible**: `signaire-model` and `signaire-bundle` stay on Java 17, without Spring, with few
  dependencies (ArchUnit checks it), so that the Android app reads the same bundles.
- **Signs keyed by SignSuisse uid**: loading is idempotent, and everything else refers to signs by uid (signs are
  transient, see [Bundle loading](bundle-loading.md)).
- **Media paths checked on writing and reading**: a bundle can't write or read outside its media directory
  (`media/../../secret.txt` is refused).

## Open questions

- The [Android app](android.md) reads bundles with Jackson; if it proves heavy there, a lighter JSON library.
