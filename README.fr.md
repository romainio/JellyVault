<p align="center"><img src="docs/assets/jellyvault-logo.png" width="128" alt="Logo JellyVault"></p>
<h1 align="center">JellyVault</h1>
<p align="center">Ta bibliothèque Jellyfin avec un accueil de plateforme de streaming sur Android TV : découverte TMDB et demandes Radarr/Sonarr depuis le canapé.</p>
<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%20TV%20%7C%20Fire%20TV-7B4DFF" alt="Plateforme : Android TV et Fire TV">
  <img src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 6.0 ou plus">
  <img src="https://img.shields.io/badge/works%20with-Jellyfin%2010.10%20%7C%2010.11-00A4DC?logo=jellyfin&logoColor=white" alt="Fonctionne avec Jellyfin 10.10 et 10.11">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin et Jetpack Compose">
  <img src="https://img.shields.io/badge/license-GPL--2.0-2ea44f" alt="Licence : GPL-2.0">
</p>
<p align="center"><a href="README.md">English</a> · <b>Français</b> · <a href="README.es.md">Español</a> · <a href="README.de.md">Deutsch</a></p>

> **Android TV et Fire TV uniquement.** JellyVault a besoin de ton propre serveur [Jellyfin](https://jellyfin.org). La
> découverte TMDB et les demandes Radarr/Sonarr sont facultatives : sans elles, c'est un client Jellyfin classique.

JellyVault transforme ta bibliothèque Jellyfin en accueil façon plateforme de streaming : tendances, recommandations
d'après ce que tu as regardé, studios et genres. Les titres que tu n'as pas encore s'ouvrent sur une page complète, et un
bouton les envoie à Radarr ou Sonarr.

**Aide :** le [guide d'utilisation](docs/help/fr.md) couvre l'installation, la clé TMDB, Radarr/Sonarr, les demandes, les
notes, les mises à jour et le dépannage.

![JellyVault, écran d'accueil](docs/screenshots/home.jpg)

## Ce que fait JellyVault

- **Accueil en rangées** : reprendre la lecture, tendances, *Parce que tu as regardé…*, *Films pour toi*, ta watchlist
  TMDB et des étagères thématiques.
- **Pages Films et Séries** : recommandations, genres, studios & sagas ou chaînes, et tendances par plateforme de
  streaming.
- **Pages de titre plein écran** avec casting, bande-annonce et *Titres similaires*, et lecture de ta copie si tu l'as.
- **Demandes** : un film à Radarr, une série, une saison ou toutes les saisons à Sonarr, avec l'état du téléchargement
  sur la page du titre.
- **Recherche** dans ta bibliothèque et sur TMDB en même temps.
- **Liaison du compte TMDB** : tes notes et ta watchlist alimentent les recommandations et marquent comme vus les titres
  notés.
- **Client Jellyfin** hérité de Wholphin : vues de bibliothèque, lecture ExoPlayer, sous-titres, TV en direct, musique,
  trickplay.
- **Langues** : interface en anglais et en français ; titres, synopsis et genres TMDB suivent la langue de l'interface.

![JellyVault, page d'un titre](docs/screenshots/title.jpg)

![JellyVault, demande d'une série](docs/screenshots/request.jpg)

| | |
|---|---|
| ![Parce que tu as regardé](docs/screenshots/because-you-watched.jpg) | ![Recherche](docs/screenshots/search.jpg) |
| Recommandations d'après ce que tu as regardé | Recherche dans ta bibliothèque et sur TMDB (✓ = dans ta bibliothèque) |
| ![Studios](docs/screenshots/studios.jpg) | ![Genres](docs/screenshots/genres.jpg) |
| Studios & sagas | Genres |

Les captures sont prises avec l'interface en anglais.

## Confidentialité

Tout reste sur la TV. Ta clé TMDB et les adresses, clés et mots de passe Radarr/Sonarr ne sont stockés que sur
l'appareil. JellyVault se connecte à ton serveur Jellyfin, à TMDB, à tes serveurs Radarr/Sonarr, à un top 10
hebdomadaire public, et à GitHub pour vérifier les mises à jour. Aucune statistique d'usage : après un plantage, le rapport n'est
envoyé que si tu l'acceptes, et uniquement à ton propre serveur Jellyfin.

## Installation

Télécharge l'APK dans [Releases](https://github.com/romainio/JellyVault/releases) une fois une version publiée,
et installe-le sur ton Android TV ou ta Fire TV (autorise d'abord les applications de sources inconnues). Les réglages et
la connexion sont conservés quand une mise à jour s'installe par-dessus une application signée avec la même clé.

Prérequis : Android TV ou Fire TV sous Android 6.0 ou plus, un serveur Jellyfin `10.10.x` ou `10.11.x`. Une
[clé API TMDB](https://www.themoviedb.org/settings/api) gratuite pour la découverte, et Radarr/Sonarr si tu veux les
demandes.

Suis ensuite le [guide d'utilisation](docs/help/fr.md) : connexion à Jellyfin, clé TMDB dans *Paramètres › TMDB et notes
IMDb*, et les entrées *Radarr* et *Sonarr* des paramètres.

## Limites connues

- Les descriptions des titres que tu possèdes viennent de ton serveur Jellyfin, dans la *Langue de métadonnées
  préférée* réglée pour chaque bibliothèque sur le serveur. Les textes TMDB suivent la langue de l'interface.
- La découverte utilise la France comme région : tendances et sorties, classifications d'âge, étagères « Films
  français » et top 10 de la semaine.
- Les écrans propres à JellyVault sont traduits en anglais et en français uniquement. Dans les autres langues, ces
  écrans s'affichent en anglais.

## Feuille de route

- **Versions sur GitHub** avec des APK signés et des mises à jour dans l'application servies depuis GitHub.
- **Région de découverte** qui suit le pays de la TV au lieu de la France.
- **Plus de langues** pour les écrans JellyVault.

## Compilation

```
./gradlew :app:assembleDefaultDebug
./gradlew :app:testDefaultDebugUnitTest
```

JDK 21 (le JBR fourni avec Android Studio convient) et le SDK Android. Pour intégrer une clé TMDB par défaut à tes
propres builds, ajoute `tmdb.readToken=<ton jeton d'accès en lecture TMDB>` à `local.properties` (non suivi par git : ne
commite jamais ton jeton). Voir [DEVELOPMENT.md](DEVELOPMENT.md) pour l'organisation du projet et
[Intents.md](Intents.md) pour l'API d'intents.

## Retours

Bugs et idées sont les bienvenus dans les [Issues](https://github.com/romainio/JellyVault/issues). Lis
[CONTRIBUTING.md](CONTRIBUTING.md) avant d'ouvrir une pull request.

## Crédits

- [Wholphin](https://github.com/damontecres/Wholphin) par damontecres : JellyVault est parti d'un fork de ce projet,
  dont vient l'essentiel du code client Jellyfin. Sa licence GPL-2.0 s'applique.
- [Jellyfin](https://jellyfin.org) et l'[application Android TV officielle de Jellyfin](https://github.com/jellyfin/jellyfin-androidtv),
  dont le code a été adapté pour le profil de lecture directe de l'appareil.
- [TMDB](https://www.themoviedb.org) pour les titres, images et métadonnées. This product uses the TMDB API but is not
  endorsed or certified by TMDB. (Ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB.)
- [Radarr](https://radarr.video) et [Sonarr](https://sonarr.tv) pour les demandes de films et de séries.

## Licence

GPL-2.0, voir [LICENSE](LICENSE). Le code est modifié à partir de Wholphin par damontecres (GPL-2.0) ; modifications
depuis 2026.

Jellyfin, TMDB, Android TV et Fire TV sont des marques de leurs propriétaires respectifs ; les noms, logos et
affiches visibles dans les captures appartiennent à leurs propriétaires. JellyVault est un projet indépendant, ni affilié
à eux ni approuvé par eux.
