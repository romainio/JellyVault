# Aide JellyVault

[English](en.md) · **Français** · [Español](es.md) · [Deutsch](de.md) · [Retour au README](../../README.fr.md)

- [1. Installer](#1-installer)
- [2. Se connecter à Jellyfin](#2-se-connecter-à-jellyfin)
- [3. Ajouter ta clé TMDB](#3-ajouter-ta-clé-tmdb)
- [4. Connecter Radarr et Sonarr](#4-connecter-radarr-et-sonarr)
- [5. Demander un film ou une série](#5-demander-un-film-ou-une-série)
- [6. Lier ton compte TMDB (notes et watchlist)](#6-lier-ton-compte-tmdb-notes-et-watchlist)
- [7. Langue](#7-langue)
- [8. Réglages du catalogue](#8-réglages-du-catalogue)
- [9. Mises à jour](#9-mises-à-jour)
- [10. Dépannage](#10-dépannage)

Les captures sont prises avec l'interface en anglais ; les libellés français sont indiqués dans le texte.

## 1. Installer

1. Sur la TV, autorise l'installation d'applications de sources inconnues pour l'application qui servira à installer (par exemple *Downloader* ou un gestionnaire de fichiers).
2. Télécharge `JellyVault.apk` sur la page [Releases](https://github.com/romainio/JellyVault/releases), ou l'APK adapté à ton appareil (`JellyVault-armeabi-v7a.apk` pour la plupart des TV, `JellyVault-arm64-v8a.apk` pour les box récentes).
3. Ouvre le fichier pour l'installer. Depuis un ordinateur, tu peux aussi lancer `adb install JellyVault.apk`.

JellyVault demande Android TV ou Fire TV sous Android 6.0 ou plus, et un serveur Jellyfin `10.10.x` ou `10.11.x`.

Au démarrage, le logo s'illumine quelques instants, puis l'accueil s'ouvre.

## 2. Se connecter à Jellyfin

Au premier lancement, ajoute l'adresse de ton serveur Jellyfin et connecte-toi avec ton compte Jellyfin. JellyVault mémorise le serveur et se reconnecte tout seul ensuite (*Paramètres › Connexion automatique*).

Même sans les étapes suivantes, JellyVault est déjà un client Jellyfin complet : bibliothèques, lecture, sous-titres, TV en direct et musique.

## 3. Ajouter ta clé TMDB

TMDB alimente les rangées découverte (tendances, genres, studios, recommandations) et les pages des films et séries que tu n'as pas.

1. Crée un compte gratuit sur [themoviedb.org](https://www.themoviedb.org), puis ouvre <https://www.themoviedb.org/settings/api> et demande une clé API.
2. Sur la TV, ouvre *Paramètres › TMDB et notes IMDb* et choisis *Clé API TMDB*.
3. Saisis la *clé API* (32 caractères) ou le *jeton d'accès en lecture* (un long texte qui commence par `eyJ`). Les deux fonctionnent.
4. Relance l'application : les rangées découverte se chargent au lancement suivant.

![Clé TMDB](../screenshots/tmdb-key.jpg)

La clé reste sur la TV et n'est envoyée qu'à TMDB. Une clé avec un espace ou un caractère manquant est refusée avec *Cette clé semble invalide*. Pour supprimer la clé, enregistre un champ vide.

*Intégrée à cette version* signifie que le build contient déjà une clé (seulement pour les builds compilés avec `tmdb.readToken` dans `local.properties`). Une clé saisie la remplace.

Sur un clavier de TV, valide avec la touche ✓ du clavier, puis choisis *Enregistrer*.

![Saisie de la clé](../screenshots/tmdb-key-dialog.jpg)

## 4. Connecter Radarr et Sonarr

Les demandes sont facultatives. Elles utilisent [Radarr](https://radarr.video) pour les films et [Sonarr](https://sonarr.tv) pour les séries.

1. Ouvre *Paramètres*. La section *Demandes et notes* contient *Radarr* et *Sonarr*.
2. Dans chacun, renseigne :
   - **URL du serveur** : l'adresse que tu ouvres dans un navigateur, avec son chemin s'il y en a un, par exemple `https://radarr.example.com` ou `http://192.168.1.10:7878`.
   - **Clé API** : dans Radarr ou Sonarr, *Settings › General › Security › API Key*.
   - **Identifiant / mot de passe d'accès** (facultatif) : seulement si Radarr ou Sonarr sont derrière un reverse proxy avec authentification HTTP basique.
3. Choisis **Tester la connexion**. La version de Radarr ou Sonarr s'affiche avec ✓ quand tout est bon.
4. Choisis le **Profil de qualité** et le **Dossier racine** utilisés pour les nouvelles demandes.

![Paramètres](../screenshots/settings.jpg)

![Réglages Radarr](../screenshots/radarr.jpg)

## 5. Demander un film ou une série

Ouvre un titre que tu n'as pas, depuis l'accueil, les pages Films et Séries ou la recherche.

- **Film** : *Demander* l'envoie tout de suite à Radarr.
- **Série** : *Demander* propose quoi envoyer à Sonarr : *Toutes les saisons*, *Dernière saison* ou *Choisir…* pour des saisons précises.

![Demander une série](../screenshots/request.jpg)

Le bouton suit ensuite le téléchargement : *Demandé*, *Téléchargement 42 %*, *Disponible bientôt*, puis *Regarder* une fois le fichier dans Jellyfin. *Actualiser* demande à Jellyfin de scanner sa bibliothèque quand un téléchargement est fini mais que le titre n'apparaît pas encore. Pour une série dont une partie des saisons est déjà là, le bouton affiche *2/5 saisons · Demander d'autres saisons*.

Si le bouton indique *Configurer Radarr* ou *Configurer Sonarr*, le service n'est pas encore réglé : le bouton ouvre ses réglages.

## 6. Lier ton compte TMDB (notes et watchlist)

Lier ton compte TMDB améliore les recommandations :

- tes notes alimentent les rangées *Parce que tu as regardé…*, *Films pour toi* et *Séries pour toi*,
- les titres que tu as notés sont marqués comme vus,
- ta watchlist TMDB apparaît dans *Ta liste* sur l'accueil.

Ouvre *Paramètres › TMDB et notes IMDb › Lier mon compte TMDB*, scanne le code avec ton téléphone, connecte-toi à TMDB et appuie sur *Approve*, puis choisis *C'est validé* sur la TV. *Délier* supprime la liaison.

**Noter depuis la TV** (dans *Paramètres*) ajoute un bouton *Noter* sur les pages de titre. Désactivé, les notes viennent seulement de ton compte TMDB.

**Notes IMDb** : JellyVault lit les notes sur TMDB. Pour utiliser tes notes IMDb, copie-les sur ton compte TMDB avec un outil de synchro IMDb → TMDB que tu fais tourner toi-même.

## 7. Langue

*Paramètres › Langue de l'interface utilisateur* change la langue de l'application. Les titres, synopsis, genres, logos et bandes-annonces TMDB la suivent.

- Les écrans JellyVault sont traduits en anglais et en français. Dans les autres langues, ces écrans s'affichent en anglais.
- Les descriptions des titres que tu possèdes viennent de ton serveur Jellyfin, dans la *Langue de métadonnées préférée* de chaque bibliothèque. Change-la sur le serveur et actualise les métadonnées de la bibliothèque pour les avoir dans une autre langue.
- La découverte utilise la France comme région : tendances, classifications d'âge et étagères françaises.

## 8. Réglages du catalogue

*Paramètres › Paramètres avancés › Catalogue* règle ce que proposent les rangées TMDB :

- *Titres proposés depuis* : aucune suggestion sortie avant l'année choisie (de 1970 à 2010). La recherche et les sagas montrent toujours tous les titres.
- *Notoriété des titres* : *Équilibrée*, *Découverte : titres moins connus aussi* ou *Grands succès seulement*.
- *Afficher « Continuer à regarder »* et *Afficher « Récemment ajouté »* masquent ces rangées sur l'accueil, les pages Films et Séries et les bibliothèques.

## 9. Mises à jour

L'APK de la page Releases vérifie sur GitHub au démarrage s'il existe une nouvelle version et affiche *Update available* le cas échéant. La section *À propos* des *Paramètres* indique aussi si une mise à jour est disponible. Tu peux désactiver la vérification dans *Paramètres › Paramètres avancés › Vérifier automatiquement les mises à jour*.

Installer une nouvelle version par-dessus l'ancienne garde tes réglages, à condition que les deux soient signées avec la même clé. Un build que tu as compilé toi-même (signé avec ta propre clé) ne peut pas être mis à jour par l'APK des Releases : il faut d'abord le désinstaller, ce qui efface les réglages de l'application.

## 10. Dépannage

**Pas de rangées découverte, ou « TMDB non configuré : ajoutez votre clé dans Paramètres › TMDB et notes IMDb »**
Ajoute une clé TMDB ([étape 3](#3-ajouter-ta-clé-tmdb)), puis relance l'application.

**Erreurs Radarr / Sonarr**

| Message | À vérifier |
|---|---|
| *Radarr injoignable* | L'URL du serveur, et que la TV peut joindre cette adresse |
| *Clé API refusée par Radarr* | La clé API |
| *Accès refusé par le serveur devant Radarr* | L'identifiant et le mot de passe d'accès de ton reverse proxy |
| *Réglages Radarr invalides (URL ou clé)* | Une faute dans l'URL (par exemple `htts://`) ou dans la clé |
| *Introuvable dans Sonarr* | Sonarr ne trouve pas la série, souvent parce que TMDB n'a pas d'identifiant TVDB pour elle |

**Le clavier de la TV perd des caractères, ou le D-pad ne sort pas d'un champ de texte**
Certains claviers de TV se comportent ainsi. Tape lentement, vérifie le champ avant d'enregistrer, et valide avec la touche ✓ du clavier.

**L'économiseur d'écran ou la chaîne « Récemment ajouté » a disparu après une mise à jour**
Choisis de nouveau l'économiseur JellyVault dans les paramètres d'Android TV, et autorise de nouveau la chaîne depuis les réglages des chaînes de l'écran d'accueil.

**Récupérer les journaux pour signaler un bug**
Voir [DEVELOPMENT.md](../../DEVELOPMENT.md#gathering-app-logs), puis [ouvre une issue](https://github.com/romainio/JellyVault/issues) avec les journaux, la version de l'application (*Paramètres › Version installée*) et le modèle de ta TV.
