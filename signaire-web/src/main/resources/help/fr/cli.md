# Ligne de commande

Signaire a aussi une ligne de commande, pour extraire les données de SignSuisse, les charger, chercher et exporter
sans navigateur. Elle suit la langue du système.

| Commande | Rôle |
|---|---|
| `signaire extraire` | Extrait des catégories de SignSuisse dans des bundles |
| `signaire charger` | Charge les bundles dans l'index de recherche |
| `signaire chercher` | Cherche des signes |
| `signaire exporter` | Exporte une recherche en PDF, paquet Anki ou EPUB |

`signaire <commande> --help` décrit toutes les options. Par exemple :

```
signaire extraire --categorie ville,nature --langue fr
signaire charger
signaire chercher lac --langue fr
signaire exporter -f pdf -o lac.pdf --papier a5 --mise-en-page livret lac
```

## Où sont les fichiers

Les bundles sont dans `~/.local/share/signaire/bundles` et les médias téléchargés dans `~/.cache/signaire/media`
(sous Linux ; les dossiers habituels sous macOS et Windows). La configuration de l'utilisateur est lue dans
`~/.config/signaire/application.properties`. Les variables `XDG_DATA_HOME`, `XDG_CACHE_HOME` et `XDG_CONFIG_HOME`
déplacent ces dossiers.
