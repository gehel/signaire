# Befehlszeile

Signaire hat auch eine Befehlszeile, um die Daten von SignSuisse zu extrahieren, zu laden, zu suchen und zu
exportieren, ohne Browser. Sie folgt der Sprache des Systems; Befehle und Optionen bleiben französisch.

| Befehl | Zweck |
|---|---|
| `signaire extraire` | Extrahiert Kategorien von SignSuisse in Bundles |
| `signaire charger` | Lädt die Bundles in den Suchindex |
| `signaire chercher` | Sucht Gebärden |
| `signaire exporter` | Exportiert eine Suche als PDF, Anki-Stapel oder EPUB |

`signaire <Befehl> --help` beschreibt alle Optionen. Zum Beispiel:

```
signaire extraire --categorie stadt,natur --langue de
signaire charger
signaire chercher see --langue de
signaire exporter -f pdf -o see.pdf --papier a5 --mise-en-page broschuere see
```

## Wo die Dateien sind

Die Bundles liegen in `~/.local/share/signaire/bundles`, die heruntergeladenen Medien in `~/.cache/signaire/media`
(unter Linux; die üblichen Ordner unter macOS und Windows). Die Konfiguration des Benutzers wird aus
`~/.config/signaire/application.properties` gelesen. Die Variablen `XDG_DATA_HOME`, `XDG_CACHE_HOME` und
`XDG_CONFIG_HOME` verschieben diese Ordner.
