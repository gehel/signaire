# TODO

Tasks to come, roughly in the order they will be done. Check an item when it is merged.

- [x] convert this file to markdown TODO.md
- [x] ensure that no automated test ever accesses <https://www.sgb-fss.ch>. For tests, create dummy bundles, or mock the HTTP requests
- [x] Create a README
- [x] Choose a license for the code (Apache 2.0)
  - Apache 2.0 license choosen by Guillaume
- [x] documentation / help, available contextually, and as a manual
- [x] integration test in separate module, that runs the full stack, including elasticsearch and DB
- [x] introduce archunit to document and enforce dependencies
- [x] Follow XDG specs (<https://specifications.freedesktop.org/basedir/latest/>)
- [x] Ensure that signs data can be treated as transient. Deleting all signs bundles and reloading them from scratch should work out of the box, that means that all IDs should be stable in that context.
- [x] in the export, only show options relevant to the currently selected export type. For example, don't show "Options du PDF" when we're creating an Anki deck
- [x] Add links to Anki where it makes sense and document what this export is
- [x] Add links back to <https://www.sgb-fss.ch/signsuisse/fr> in the footer, and for each sign link back to the original page
- [x] Make the web app as accessible as possible (Lighthouse accessibility score 100 on the measured pages, enforced in CI)
- [x] add integration with sitespeed.io during build to evaluate performance and fail the build in case of regression (fails when exceeding the budget of `.sitespeed/budget.json`)
- [ ] compare sitespeed.io measures with a baseline, rather than with fixed budgets
- [x] integration with lighthouse ci (thresholds in `.lighthouserc.json`)
- [x] integration with sonarcloud during the build, failing the build in case of regressions (quality gate of the `gehel-github` organization)
- [ ] (deferred: modern Elasticsearch can no longer be embedded in a JVM, the search engine of this package is to be decided) Create a package that can be run as a standalone webapp, for standard users who don't want to deploy on a real server. It should have:
  - app should be able to run just by double clicking the jar file (if a JRE is present)
  - also provide a version with an embedded JRE
  - elasticsearch runs embedded in the same jar, no need to run a docker image or connect to an external instance
  - embedded DB
  - state (DB, elasticsearch) should be persisted in a reasonable location, OS specific
  - uses the same logic / implementation, this is mostly a repackaging, not a new implementation
  - option to run single user, without having to create a user at all
  - first run should guide the user through the setup, choosing single user mode or multi user, downloading sign bundles, etc...
  - automatically open browser when the app start
  - should work on Linux, Windows and MacOS
- [x] guided presentation: on first use, the user is guided through the various features of the app
- [ ] Android app (on hold)
- [ ] iOS app (to be decided)
