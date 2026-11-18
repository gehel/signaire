# Command line

Signaire also has a command line, to extract the data of SignSuisse, load it, search and export without a browser.
It follows the language of the system; commands and options stay French.

| Command | Role |
|---|---|
| `signaire extraire` | Extracts categories from SignSuisse into bundles |
| `signaire charger` | Loads the bundles into the search index |
| `signaire chercher` | Searches signs |
| `signaire exporter` | Exports a search as a PDF, an Anki deck or an EPUB |

`signaire <command> --help` describes all the options. For example:

```
signaire extraire --categorie city,nature --langue fr
signaire charger
signaire chercher lake --langue fr
signaire exporter -f pdf -o lake.pdf --papier a5 --mise-en-page booklet lake
```

## Where the files are

Bundles are in `~/.local/share/signaire/bundles` and downloaded media in `~/.cache/signaire/media` (on Linux; the
usual folders on macOS and Windows). The configuration of the user is read from
`~/.config/signaire/application.properties`. The `XDG_DATA_HOME`, `XDG_CACHE_HOME` and `XDG_CONFIG_HOME` variables
move these folders.
