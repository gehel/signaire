# Exports

## Why

Learners revise away from the screen and in other tools: printed sheets and flashcards for a class, an Anki deck for
spaced repetition, an e-book on a phone or tablet. Any word list or search result can be exported in these formats,
from the web application and the CLI.

## Design

`signaire-export` is plain Java. The caller gives an `ExportRequest` (title, signs, locale) and a `MediaSource` that
reads downloaded media (the web application and the CLI adapt `MediaLibrary`); labels are in `export*.properties`, in
the four languages.

- **PDF** (`PdfExporter`, PDFBox): A4, A5 or A6; 1 to 12 signs per page; three layouts (`PdfLayout`):
  - *sheets*: pages in order;
  - *booklet*: half pages imposed in saddle-stitch order on landscape sheets, printed duplex on the short edge, folded;
  - *flashcards*: cards to cut, pages of images and QR codes alternating with pages of words, placed so that each
    word is behind its image when printed duplex on the long edge.

  Each sign has its image and a QR code (`QrCodes`, ZXing) to its SignSuisse video (or page). Text uses the
  standard Helvetica fonts (WinAnsi; other characters become `?`).
- **Anki**: see [Anki export](anki-export.md).
- **EPUB 3** (`EpubExporter`): a page per sign, with its video included (when downloaded and asked for) or linked.
- Web: `/lists/{id}/export` and `/export` (a search), with a form for the format and its options
  (`ExportController`); CLI: `signaire exporter` (`ExportCommand`, `Exports`).

## Decisions

- **Plain Java, no Spring**: the same code for the web application and the CLI, testable without a context
  (ArchUnit keeps Spring out).
- **`MediaSource` instead of reading bundles**: exporters don't know where media live; the web application and the CLI
  read them from their media library, and missing media fall back to SignSuisse links.
- **Standard PDF fonts**: no font files to ship or license, small PDFs; the cost is the WinAnsi character set, enough
  for French, German and Italian.
- **QR codes to the videos**: paper can't play videos; a phone can scan the code next to the sign.
- **Booklet and flashcards imposed by Signaire**: printers' drivers impose differently or not at all; doing it in the
  PDF gives the same result everywhere (with the duplex setting the help names).
- **EPUB 3 with optional video inclusion**: included videos make large books; linked ones need a connection.

## Open questions

- Characters outside WinAnsi (rare in sign names) become `?` in PDFs; embedding a font would fix it at the cost of
  size.
