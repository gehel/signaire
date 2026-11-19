# Web interface

## Why

Most users search and read signs in a browser, on a phone in class as well as on a computer; many are not
technical. The interface must be fast, accessible, usable without JavaScript, and in the user's language.

## Design

`signaire-web`: Spring MVC with Thymeleaf templates and htmx for partial updates; no JSON API yet.

- **Pages**: search (`/`, with filters by language and category, paging, search as you type), sign pages
  (`/signs/{uid}`: videos, images, examples, dialect variants, link to SignSuisse), lists, requests, exports, help,
  administration. `layout.html` holds the shared fragments.
- **htmx**: requests marked by htmx get fragments only (`Htmx.isPartialRequest`), except history restoration;
  responses vary on `HX-Request`. The language links are refreshed out of band. Forms work without script (plain
  POST and redirect), htmx improves them.
- **Media** (`/media/{*path}`): served from the bundles by `MediaLibrary` (core), with range requests (video seeking)
  and a week of caching; missing media link to SignSuisse.
- **Help** (`/help/{topic}`): Markdown pages per topic and language (`help/<lang>/`, CommonMark), linked from the
  features they describe.
- **Guided tour** (`?tour=N`): each step a `<dialog>` in the header pointing at an element marked `data-tour`;
  `tour.js` (~50 lines, no library) places it and dims the rest of the page; without script it's a card under the
  header. `/tour/done` remembers in a cookie that it was seen.
- **Measures** (CI): Lighthouse (accessibility must stay at 1) and sitespeed.io (a budget, and no regression of
  more than 10% against main), on demo bundles of invented signs.

## Decisions

- **Server-rendered HTML with htmx, no SPA**: one codebase, no build of JavaScript, fast first paint on phones, works
  without script; htmx covers what needs to feel immediate (search as you type, adding to lists, progress).
- **Thymeleaf**: natural templates readable as HTML, Spring integration (messages, security, CSRF).
- **No CDN**: htmx comes from a webjar; the standalone application works offline and no third party sees users.
- **Accessibility as a gate**: a learning tool for a community that cares about accessibility; Lighthouse at 1 in CI.
- **Help in Markdown in the repository**: updated with the features in the same pull request, translated like the
  messages.
- **Links as a model attribute** (`Links`): Thymeleaf 3.1 forbids bean references in link attributes.

## Open questions

- The REST API for the Android app and synchronization is still to design.
