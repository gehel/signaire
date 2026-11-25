# Administration

Les administrateurs sont les utilisateurs désignés dans la configuration (`signaire.accounts.admins`). Ils voient
« Administration » dans l'en-tête.

## Modifier les signes

- **Nouveau signe** : pour ajouter un signe absent de SignSuisse.
- Sur la page d'un signe, **Modifier le signe** et **Supprimer le signe**.
- **Signes modifiés** liste les changements ; « Annuler » rétablit le signe de SignSuisse.

Les données des signes peuvent être rechargées à tout moment : les changements des administrateurs sont conservés à
part et appliqués à nouveau après chaque chargement.

## Bundles

Les signes arrivent dans des **bundles**, un fichier `.zip` par catégorie de SignSuisse. La page **Bundles** les
liste, en accepte un nouveau (il remplace celui de la même catégorie), recharge les bundles nouveaux ou modifiés,
ou réindexe tout (après une mise à jour de Signaire qui change l'index).

## Synchronisation avec SignSuisse

Choisissez des catégories et des langues des signes (aucune cochée : toutes), et les médias à télécharger : vidéos et
images, vidéos seulement, images seulement, ou aucun. Les médias non téléchargés sont montrés depuis SignSuisse, mais
manquent aux exports et sans connexion. La synchronisation tourne en arrière-plan ; chaque signe est consultable dès
qu'il est extrait (ses médias montrés depuis SignSuisse jusqu'à la fin de sa catégorie).

Signaire ménage SignSuisse : les requêtes sont espacées d'au moins une demi-seconde, et les signes déjà extraits sont
repris des bundles existants plutôt que relus ; cochez **Lire à nouveau les signes déjà extraits** pour obtenir les
corrections faites depuis sur SignSuisse. Les signes trouvés sont lus, et leurs médias téléchargés, pendant que la
recherche continue. La page montre où en est la catégorie en cours (recherches, signes, médias : faits, restants, en
échec), ce qui se fait en ce moment, une estimation du temps restant et les catégories restantes. Sans médias, une
catégorie prend de quelques minutes à une demi-heure ; avec les médias, bien plus. Un média qui échoue reste montré
depuis SignSuisse ; la synchronisation suivante le retente.

## Contact pour SignSuisse

Chaque requête à SignSuisse dit qu'elle vient de Signaire, avec un lien vers le projet. Ajoutez-y votre adresse
e-mail : si Signaire charge trop son site, SignSuisse pourra vous écrire plutôt que de bloquer vos téléchargements.
Écrivez-la dans le fichier `application.properties` du répertoire de configuration de Signaire
(`~/.config/signaire/` sous Linux), utilisé aussi par la ligne de commande :

```properties
signaire.signsuisse.contact=vous@exemple.org
```

**Vie privée** : l'adresse est envoyée à SignSuisse avec chaque requête, en clair dans l'en-tête `User-Agent`.
SignSuisse (la Fédération Suisse des Sourds) et son hébergeur la verront dans leurs journaux et la garderont selon
leurs règles ; quiconque voit passer ces requêtes peut la lire aussi. Utilisez une adresse que vous acceptez de
partager, une adresse dédiée par exemple. `signaire.signsuisse.user-agent` remplace l'en-tête entier.

## Demandes de signes

Pour chaque demande en attente : **Chercher sur SignSuisse** puis **Importer** le bon signe (ou l'**associer** s'il
est déjà dans le lexique), **Créer le signe** vous-même, ou **Refuser** la demande. L'utilisateur voit le résultat
dans ses demandes.
