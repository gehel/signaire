# Riga di comando

Signaire ha anche una riga di comando, per estrarre i dati di SignSuisse, caricarli, cercare ed esportare senza
browser. Segue la lingua del sistema; comandi e opzioni restano in francese.

| Comando | Ruolo |
|---|---|
| `signaire extraire` | Estrae categorie da SignSuisse in bundle |
| `signaire charger` | Carica i bundle nell'indice di ricerca |
| `signaire chercher` | Cerca segni |
| `signaire exporter` | Esporta una ricerca in PDF, mazzo Anki o EPUB |

`signaire <comando> --help` descrive tutte le opzioni. Per esempio:

```
signaire extraire --categorie citta,natura --langue it
signaire charger
signaire chercher lago --langue it
signaire exporter -f pdf -o lago.pdf --papier a5 --mise-en-page libretto lago
```

## Dove sono i file

I bundle sono in `~/.local/share/signaire/bundles` e i media scaricati in `~/.cache/signaire/media` (su Linux; le
cartelle abituali su macOS e Windows). La configurazione dell'utente è letta in
`~/.config/signaire/application.properties`. Le variabili `XDG_DATA_HOME`, `XDG_CACHE_HOME` e `XDG_CONFIG_HOME`
spostano queste cartelle.
