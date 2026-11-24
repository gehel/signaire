# Visual design of the web interface

## Why

The web interface works, but it looks like a bare document: the browser's form controls, little hierarchy, search
results as a plain list, the video of a sign lost among links. Its users learn sign languages, often on a phone in
class, and many are not technical: the interface should be pleasant and make the important things obvious (the
search, then the signs, then the video of a sign).

The constraints of the [web interface](web-ui.md) stay: everything works without script, no CDN (the standalone
application works offline, no third party sees users), no build of JavaScript or CSS, accessibility at 1 in
Lighthouse, and the budget of sitespeed.io.

## Design

- **Pico CSS** (`org.webjars.npm:picocss__pico`, served like htmx from its webjar) styles the semantic HTML that the
  templates already have: typography, forms, buttons, tables, `details`, in a light and a dark theme that follow the
  system (`prefers-color-scheme`). The page loads its red theme (`pico.red.min.css`), then `signaire.css`.
- **`signaire.css`** gives the identity through Pico's custom properties (the brand red `#b5121b` of the favicon,
  radius, spacing), and lays out our pages. Its own colors are custom properties too, with values for the dark theme.
  Styles hang on the semantic markup and the classes the templates already have; templates change where a layout
  needs a container (the two columns of a sign page) or a class says what something is (a secondary button, the
  language of a sign).
- **Page frame**: header, main and footer span the window and center their content with padding (at most 72rem
  wide), so no wrapper element is needed. The header has two rows: the tagline and the languages of the interface,
  then the brand and the links. On phones: the brand, the help and the languages (their codes, FR DE IT EN, read by
  screen readers as their names), then the links.
- **Search**: a panel with a large search field and its button side by side, the sign languages as chips (labels
  of checkboxes), and the category. Results are a grid of cards: the name of the sign (its link covers the card),
  the badge of its sign language, its categories, the link to SignSuisse; the checkbox to pick it for a list.
  Lists show their signs the same way.
- **Sign languages** have a color each (LSF, DSGS, LIS), on the badges of cards, sign pages and requests, with their
  abbreviation written: the color helps, it is never the only cue.
- **Sign page**: the media first and large, the details as a description list, the categories as chips, examples and
  regional variants as cards; on wide screens, the lists, the administration and the link to SignSuisse are in a side
  column.
- **Forms of accounts** (login, registration, setup) are a card in a narrow column; forms alone on a page and
  accordions are cards; the administration keeps Pico's tables, its menu is a grid of cards.
- **What Pico does that we undo**: it sets `--pico-color` on links and buttons (`--ink` is the color of text in the
  header), makes submit buttons as wide as their form, every `dialog` a modal that covers the window (the guided
  tour's dialog is a card), and shows focus with shadows that cards hide (an outline instead). Its light theme's
  selector also matches when the system is dark: our colors are set again in the dark block.
- **Compression**: Spring Boot compresses text responses (`server.compression.enabled`): Pico weighs 83 KB, 12 KB
  compressed. On the server Caddy compressed already, but the standalone application and the measures of CI did not.
- **Checked in a browser** (`BrowserIT`, in `signaire-it`): the web jar on invented signs, Chromium in a container
  driven by Selenium, both themes, a computer and a phone. It checks what the stylesheets must get right (they load
  from the jar, no page is wider than a phone, cards side by side then stacked, a card opens its sign, the tour is a
  card next to what it presents and closes) and writes screenshots of every page, kept by CI, for people to look at.

## Decisions

- **Pico CSS**, a framework for semantic HTML: the templates keep their markup, without a class on every element,
  and it needs no script.
  - Bootstrap, Bulma: classes on every element (templates rewritten, and harder to read), and Bootstrap's components
    need its JavaScript.
  - Tailwind: utility classes everywhere, and a build step to generate the CSS (or its CDN script, which is excluded).
  - Smaller classless stylesheets (Water.css, MVP.css, Simple.css): fewer components (no input and button groups, no
    accordions), and not themable through as many custom properties.
  - Only our own stylesheet: it would have to redo what Pico does well and accessibly (form controls, focus,
    contrast, the dark theme). Pico's 12 KB, cached after the first page, are worth it.
  - Pico's red theme rather than the default (azure), with our red on top: what we don't override stays in the hue of
    the brand, in both themes.
  - The full version rather than the classless one: its secondary and outline buttons tell secondary actions apart,
    for 1 KB more.
- **System fonts**, no web font: nothing to download, nothing from a third party, and a native look everywhere.
- **No icon set**: a few characters (←, →, ↗, ✓) added by CSS with an empty alternative text, so that screen readers
  skip them. An icon font or a sprite would weigh more than the few icons are worth now.
- **No theme switch**: the theme follows the system; a switch would need script and a cookie.
- **Language codes on phones only**: the names of the four languages don't fit beside the brand on a phone; elsewhere
  each language is named in itself.
- **CSS grows** from 6 KB (2 KB compressed) to 18 KB compressed: the comparison of sitespeed.io with main flags it
  once, on the pull request that brings Pico (main was measured uncompressed), and the budget (30 KB of CSS) holds.
- **Browser tests with Selenium in a container** (Testcontainers): no browser or driver to install, nothing downloaded
  by the tests, the same Chromium in CI and on every machine.
  - Playwright: downloads its browsers when tests run, and its Java version drives them through Node.
  - HtmlUnit: no layout, so nothing to say about the look.
  - Comparing screenshots with reference images: they change with every version of Chromium and its fonts; the tests
    assert the layout instead, and the screenshots are for people.

## Open questions

- Thumbnails of the signs in the results: many signs have no image, and 20 images per page would weigh on phones and
  break the budget of requests (25). Maybe with lazy loading and a small size made when bundles are written.
- A logo: the "S" of the favicon for now.
- Lighthouse checks the light theme only: the contrast of the dark theme rests on Pico's palette and ours, checked by
  hand. An accessibility check in `BrowserIT` (axe-core) would cover both themes and the pages behind a login.
