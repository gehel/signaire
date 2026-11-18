# Application autonome

L'application autonome fait tourner Signaire sur votre ordinateur, sans serveur : pas de base de données ni de
moteur de recherche à installer. Lancez-la d'un double clic (`signaire-web-…-standalone.jar` avec Java 27, ou le programme
Signaire, qui a son propre Java) : elle ouvre Signaire dans votre navigateur, à l'adresse <http://localhost:8180>. Si Signaire tourne déjà,
un nouveau lancement ouvre simplement le navigateur.

Le programme n'est pas signé : au premier lancement, macOS demande de l'ouvrir par un clic droit puis « Ouvrir »,
et Windows de confirmer dans « Informations complémentaires ».

## Premier lancement

Au premier lancement, Signaire demande :

- **qui l'utilise** : vous seulement (pas de compte ni de mot de passe : vous êtes aussi l'administrateur) ou
  plusieurs personnes (chacune crée son compte ; le premier, créé tout de suite, est celui de l'administrateur) ;
- **les signes à télécharger** de SignSuisse : choisissez quelques catégories et langues. Le téléchargement dure
  longtemps et continue pendant que vous utilisez Signaire ; vous en suivez l'avancement dans l'administration, d'où
  vous pourrez aussi télécharger d'autres catégories plus tard.

Seul cet ordinateur peut utiliser Signaire : il n'est pas accessible depuis le réseau.

## Quitter

« Quitter Signaire », en haut de la page, arrête l'application (pour l'administrateur). Fermer le navigateur ne
suffit pas.

## Où sont les fichiers

Sous Linux, les bundles sont dans `~/.local/share/signaire/bundles`, la base de données et l'index de recherche dans
`~/.local/state/signaire`, et les choix du premier lancement dans `~/.config/signaire/standalone.properties` (les
dossiers habituels sous macOS et Windows). Supprimez ce dernier fichier pour refaire les choix du premier lancement.
