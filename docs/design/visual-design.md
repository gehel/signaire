# Visual design of the web interface

## Why

Signaire's users learn Swiss sign languages, often on a phone in class, and many are not technical. The interface
should be pleasant and make the important things obvious: the search, then the signs, then the video of a sign.

A first version put Pico CSS and a stylesheet of cards on the pages: clean and accessible, but generic, the look of
any web application. This one takes its identity from its subject, a lexicon of sign languages: signs written as
glosses, entries one reads like a dictionary's, the red of Switzerland used as a signal.

The constraints of the [web interface](web-ui.md) stay: everything works without script, no CDN (the standalone
application works offline, no third party sees users), no build of JavaScript or CSS, accessibility at 1 in
Lighthouse, and the budget of sitespeed.io.

## Design

- **Pico CSS** (`org.webjars.npm:picocss__pico`, served like htmx from its webjar) styles the semantic HTML that the
  templates already have: forms, buttons, tables, `details`, in a light and a dark theme. The page loads its red
  theme (`pico.red.min.css`), then `signaire.css`, which gives the identity through Pico's custom properties and lays
  out our pages.
- **The gloss is the voice**: in sign linguistics a sign is written as a gloss, a word in capitals, as SignSuisse
  writes them (ARBRE, VALLÉE). Every sign name is set the same way, in the heaviest weight of the typeface, spaced:
  large at the top of a sign page, at the head of each entry in results and lists. It is the one bold element;
  everything around it stays quiet.
- **Typeface**: Atkinson Hyperlegible Next, drawn by the Braille Institute for legibility (I, l and 1 told apart,
  open shapes, wide capitals), served from the application (`static/fonts`, SIL Open Font License): one variable
  file for Latin, 34 KB, weights 200 to 800, preloaded by the pages. A type scale of ratio 1.25; text in sentence case,
  at most about 70 characters a line.
- **Colors**: white paper and ink; red, the brand's `#b5121b`, only for the brand, the main action of a form, the
  current choice (theme, language, checked filters) and the focus. Links are ink, underlined, red on hover. Each sign
  language has a color (LSF, DSGS, LIS) on its badge, with its abbreviation written: the color helps, it is never the
  only cue.
- **Theme**: the system's (`prefers-color-scheme`), unless the user chooses one in the header (Auto, Light, Dark).
  Like the language, the choice is a parameter (`?theme=dark`) remembered in a cookie (`ThemeAdvice`); the server
  writes it on the page, `<html data-theme="dark">`, which Pico follows. Auto forgets the choice.
- **Page frame**: header, main and footer span the window and center their content with padding (at most 72rem
  wide), so no wrapper element is needed. The header has two rows: the tagline, the theme and the languages of the
  interface, then the brand and the links. On phones: the brand, the help and the languages (their codes, FR DE IT
  EN, read by screen readers as their names), then the links and the theme (◐ ☀ ☾, read as their names).
- **Search**: the field is the first thing of the home page, large, on the page itself; the sign languages (chips)
  and the category on one line below. New visitors get one line of welcome above it, with the tour.
- **Results are entries of a lexicon**: the gloss, the badge of its sign language, then its categories and the start
  of its definition (or periphrase), in the language of the sign (`lang`), and the link to SignSuisse; the link of the
  gloss covers the entry. Rules separate the entries, in two columns on wide screens. Without a word, results come in
  alphabetical order, grouped under their initial (`h2`); with a word they come by relevance, without headings. Lists
  show their signs the same way.
- **Sign page**: the gloss as a large heading, the video below it at the width of the column, the periphrase and the
  definition as text (labels in sentence case), the categories as links, the examples as quotations in the language of
  the sign (« ») with their videos. On wide screens, the lists, the administration and the link to SignSuisse are in
  a side column.
- **Cards only for tools**: forms alone on a page, the lists of a sign, the exports and other accordions, the guided
  tour: a light fill and a border, no shadow. Results, lists, menus and the help topics are entries separated by
  rules. Only what floats over the page (the tour) has a shadow.
- **What Pico does that we undo**: it sets `--pico-color` on links and buttons (`--ink` is the color of text), makes
  submit buttons as wide as their form, every `dialog` a modal that covers the window (the guided tour's dialog is a
  card), and shows focus with shadows (an outline instead). Its light theme's selector also matches when the system is
  dark: our colors are set again in the dark blocks, one for a dark system and one for the dark theme chosen.
- **Compression**: Spring Boot compresses text responses (`server.compression.enabled`): Pico weighs 83 KB, 12 KB
  compressed. On the server Caddy compressed already, but the standalone application and the measures of CI did not.
- **Checked in a browser** (`BrowserIT`, in `signaire-it`): the web jar on invented signs, Chromium in a container
  driven by Selenium, both themes, a computer and a phone. It checks what the stylesheets must get right (they and the
  font load from the jar, no page is wider than a phone, entries in two columns then one, an entry opens its sign,
  initials when browsing, the tour is a card next to what it presents and closes, a theme chosen looks like the
  system's and is remembered) and writes screenshots of every page, kept by CI, for people to look at.

## Decisions

- **Pico CSS**, a framework for semantic HTML: the templates keep their markup, without a class on every element,
  and it needs no script.
  - Bootstrap, Bulma: classes on every element (templates rewritten, and harder to read), and Bootstrap's components
    need its JavaScript.
  - Tailwind: utility classes everywhere, and a build step to generate the CSS (or its CDN script, which is excluded).
  - Smaller classless stylesheets (Water.css, MVP.css, Simple.css): fewer components (no input and button groups, no
    accordions), and not themable through as many custom properties.
  - Only our own stylesheet: it would have to redo what Pico does well and accessibly (form controls, focus,
    contrast, the dark theme).
  - Pico's red theme rather than the default (azure), with our red on top: what we don't override stays in the hue of
    the brand, in both themes.
- **The identity comes from the subject**: glosses, the entries of a lexicon, the red of Switzerland. A grid of
  identical rounded cards with soft shadows, labels in capitals and arrows after links (the first version) is the look
  of any application; it said nothing of signs, and hid what tells two signs apart, their definitions.
- **Atkinson Hyperlegible Next**: made for legibility, for learners and for a community attentive to accessibility;
  its capitals suit glosses.
  - System fonts: nothing to download, but no voice of our own.
  - Inter and other neutral grotesques: the default of every interface.
  - A serif and an editorial page (cream paper, hairline rules): what a "lexicon" calls first, and a cliché.
  - The font from a webjar: Spring rewrites the webjar links of the templates with their version, not those of the
    stylesheet, so the preload and the stylesheet would load it twice. The file is in `static/fonts`, with its
    license.
- **One font file, no italic**: the italic would be one more request and 38 KB; the categories of an entry are muted
  rather than in italics, and the browser slants the few emphases of the manual.
- **Red is a signal**: when everything is red (links, chips, borders, tints), nothing stands out.
- **Entries rather than cards for results**: the definition tells homonyms apart, entries in two columns show as many
  signs as cards did, and the page reads like the lexicon it is. Initials only in alphabetical order, where they say
  where one is in the alphabet.
- **No labels in capitals, no decorative arrows**: capitals belong to glosses. ↗ stays after links to SignSuisse (one
  leaves the site) and ✓ before "in the list" (a state), added by CSS with an empty alternative text.
- **The theme follows the system until the user chooses one**: most users never need the switch, and Auto keeps
  following the system when it changes with the time of day.
- **The server writes the chosen theme**, as it does for the language: no script, and no flash of the other theme.
  A script would have to run before the page is painted, and the pages work without script.
- **Our dark colors are written twice**, for a dark system and for the dark theme chosen, as Pico does; BrowserIT
  checks that both look the same. `light-dark()` would write each color once, but browsers older than 2024 (Safari
  17.5) don't know it, and would lose the colors of buttons.
- **Language codes on phones only**: the names of the four languages don't fit beside the brand on a phone; elsewhere
  each language is named in itself.
- **Weight**: Pico brought the CSS from 6 KB to 18 KB compressed, the font adds 34 KB and a request. The comparison of
  sitespeed.io with main flags each once, on the pull request that brings it; the budget holds.
- **Browser tests with Selenium in a container** (Testcontainers): no browser or driver to install, nothing downloaded
  by the tests, the same Chromium in CI and on every machine.
  - Playwright: downloads its browsers when tests run, and its Java version drives them through Node.
  - HtmlUnit: no layout, so nothing to say about the look.
  - Comparing screenshots with reference images: they change with every version of Chromium and its fonts; the tests
    assert the layout instead, and the screenshots are for people.

## Open questions

- Thumbnails of the signs in the results: many signs have no image, and 20 images per page would weigh on phones and
  break the budget of requests (25). Maybe with lazy loading and a small size made when bundles are written.
- A logo of our own: the "S" of the favicon for now.
- The PDF exports use Helvetica (standard fonts, no embedding): embedding Atkinson Hyperlegible Next would make the
  printed cards look like the pages, and lift the limit to WinAnsi characters.
- Lighthouse checks the light theme only: the contrast of the dark theme rests on Pico's palette and ours, checked by
  hand. An accessibility check in `BrowserIT` (axe-core) would cover both themes and the pages behind a login.
