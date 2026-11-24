# SignSuisse synchronization

## Why

Users of the web application and of the standalone application shouldn't need the CLI to get signs: an administrator
(or the first run of the standalone application) chooses categories and languages, and Signaire extracts and loads
them. An extraction takes minutes to hours, so it must run in the background, show its progress, and be cancellable.

## Design

- `SignSuisseSync` (web) runs the [extractor](extraction.md) against the `SignSource` bean
  (`ExtractionConfiguration`: the real SignSuisse, with the configured user agent; web tests use `FakeSignSuisse`)
  in a virtual thread. One synchronization at a time; `start(categories, languages, media)` refuses a second one.
- `SyncExtraction` extracts each category into the bundle directory (media in the shared media cache). Each sign
  complete is indexed right away, in batches (every 25 signs or 2 seconds, through `BundleLoading.indexExtracted`):
  signs become searchable one by one, their media linked to SignSuisse. Each bundle is loaded as soon as it is written
  (`BundleLoading.load`), which brings the downloaded media and reapplies the changes of administrators. At the end
  (done, failed or cancelled), `BundleLoading.loaded()` does it once more.
- The progress shown is the `ExtractionStatus` of the category being extracted (see [Extraction](extraction.md#observability)),
  with the categories done (signs, failures, complete or not) and those left; it's logged at INFO every 30 s.
- `/admin/sync`: the form (categories, languages, media: none, videos, images, all; refresh the signs already
  extracted), and the progress refreshed by
  htmx polling (`/admin/sync/status`); `/admin/sync/cancel`. The page shows the user agent sent to SignSuisse, and the
  help explains the contact address and its privacy implications.
- The standalone setup (`/setup`) starts a synchronization of the chosen categories.

## Decisions

- **In process, in a virtual thread**: no job queue or worker to deploy; a synchronization is rare, and losing one
  to a restart only costs restarting it (downloaded media are cached).
- **One at a time**: two extractions would double the load on SignSuisse and write the same bundles.
- **Index each sign as soon as it's complete**, in batches: a sign is searchable seconds after it is read, rather than
  at the end of its category; batches keep the index from committing once per sign.
- **Progress as an immutable record in an `AtomicReference`**: the page reads a consistent snapshot without locks.
- **Everything visible**: searches, signs and media, done and left, with an estimate of the time left; before, the
  listing step showed nothing and the synchronization looked stuck.
- **Media off by default**: videos are large; administrators opt in.

## Open questions

- No scheduling (periodic re-synchronization); SignSuisse changes slowly.
