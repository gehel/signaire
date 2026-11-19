# SignSuisse synchronization

## Why

Users of the web application and of the standalone application shouldn't need the CLI to get signs: an administrator
(or the first run of the standalone application) chooses categories and languages, and Signaire extracts and loads
them. An extraction takes minutes to hours, so it must run in the background, show its progress, and be cancellable.

## Design

- `SignSuisseSync` (web) runs the [extractor](extraction.md) against the `SignSource` bean
  (`ExtractionConfiguration`: the real SignSuisse, with the configured user agent; web tests use `FakeSignSuisse`)
  in a virtual thread. One synchronization at a time; `start(categories, languages, media)` refuses a second one.
- `SyncExtraction` extracts each category into the bundle directory (media in the shared media cache), and loads each
  bundle as soon as it is written: signs appear category by category. At the end (done, failed or cancelled),
  `BundleLoading.loaded()` refreshes the media and reapplies the changes of administrators.
- `SyncListener` turns the extractor's events into a `Progress` record (state, category and language being listed,
  searches done and planned, signs found, extracted and failed, media queued and downloaded, categories done) and
  checks for cancellation between steps; it logs at INFO every 25 signs.
- `/admin/sync`: the form (categories, languages, media: none, videos, images, all), and the progress refreshed by
  htmx polling (`/admin/sync/status`); `/admin/sync/cancel`. The page shows the user agent sent to SignSuisse, and the
  help explains the contact address and its privacy implications.
- The standalone setup (`/setup`) starts a synchronization of the chosen categories.

## Decisions

- **In process, in a virtual thread**: no job queue or worker to deploy; a synchronization is rare, and losing one
  to a restart only costs restarting it (downloaded media are cached).
- **One at a time**: two extractions would double the load on SignSuisse and write the same bundles.
- **Load each bundle as soon as it's written**: the first categories are searchable while the others are extracted.
- **Progress as an immutable record in an `AtomicReference`**: the page reads a consistent snapshot without locks.
- **The listing step is visible** (searches done / planned): enumerating a large category takes many queries before
  the first sign; without it the synchronization looked stuck.
- **Media off by default**: videos are large; administrators opt in.

## Open questions

- No scheduling (periodic re-synchronization); SignSuisse changes slowly.
