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

Choisissez des catégories et des langues des signes (aucune cochée : toutes), et si les vidéos et images doivent
être téléchargées. La synchronisation tourne en arrière-plan ; chaque catégorie est chargée dès qu'elle est prête.
Sans médias, comptez quelques minutes par catégorie ; avec les médias, bien plus.

## Demandes de signes

Pour chaque demande en attente : **Chercher sur SignSuisse** puis **Importer** le bon signe (ou l'**associer** s'il
est déjà dans le lexique), **Créer le signe** vous-même, ou **Refuser** la demande. L'utilisateur voit le résultat
dans ses demandes.
