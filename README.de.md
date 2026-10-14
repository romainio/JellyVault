<p align="center"><img src="docs/assets/jellyvault-logo.png" width="128" alt="JellyVault-Logo"></p>
<h1 align="center">JellyVault</h1>
<p align="center">Deine Jellyfin-Bibliothek mit der Startseite eines Streamingdienstes auf Android TV: Entdecken über TMDB und Anfragen an Radarr/Sonarr direkt vom Sofa aus.</p>
<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%20TV%20%7C%20Fire%20TV-7B4DFF" alt="Plattform: Android TV und Fire TV">
  <img src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 6.0 oder neuer">
  <img src="https://img.shields.io/badge/works%20with-Jellyfin%2010.10%20%7C%2010.11-00A4DC?logo=jellyfin&logoColor=white" alt="Funktioniert mit Jellyfin 10.10 und 10.11">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin und Jetpack Compose">
  <img src="https://img.shields.io/badge/license-GPL--2.0-2ea44f" alt="Lizenz: GPL-2.0">
</p>
<p align="center"><a href="README.md">English</a> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <b>Deutsch</b></p>

> **Nur für Android TV und Fire TV.** JellyVault braucht deinen eigenen [Jellyfin](https://jellyfin.org)-Server. Das
> Entdecken über TMDB und die Anfragen an Radarr/Sonarr sind optional: Ohne sie funktioniert JellyVault als reiner
> Jellyfin-Client.

JellyVault macht aus deiner Jellyfin-Bibliothek eine Startseite im Stil eines Streamingdienstes: Titel im Trend,
Empfehlungen anhand deiner gesehenen Titel, Studios und Genres. Titel, die du noch nicht hast, öffnen sich auf einer
eigenen Seite, und ein Button schickt sie an Radarr oder Sonarr.

**Hilfe:** Die [Anleitung](docs/help/de.md) behandelt Installation, den TMDB-Schlüssel, Radarr/Sonarr, Anfragen,
Bewertungen, Updates und Fehlerbehebung.

![JellyVault, Startseite](docs/screenshots/home.jpg)

## Was JellyVault kann

- **Startseite in Reihen**: weiterschauen, Trends, *Because you watched…*, *Movies for you*, deine TMDB-Merkliste und
  thematische Regale.
- **Entdecken-Seiten** für Filme und Serien: Empfehlungen, Genres, Studios & Sagas bzw. Sender und Trend-Reihen pro
  Streamingplattform.
- **Vollbild-Titelseiten** mit Besetzung, Trailer und *More like this* sowie Wiedergabe der Kopie, die du bereits
  besitzt.
- **Anfragen**: ein Film an Radarr, eine Serie, eine Staffel oder alle Staffeln an Sonarr, mit dem Download-Status auf
  der Titelseite.
- **Suche** in deiner Bibliothek und auf TMDB gleichzeitig.
- **Verknüpfung mit dem TMDB-Konto**: Deine Bewertungen und deine Merkliste fließen in die Empfehlungen ein und
  markieren bewertete Titel als gesehen.
- **Jellyfin-Client** von Wholphin übernommen: Bibliotheksansichten, Wiedergabe mit ExoPlayer, Untertitel, Live-TV,
  Musik, Trickplay.
- **Sprachen**: Oberfläche in Englisch und Französisch; TMDB-Titel, Inhaltsangaben und Genres folgen der Sprache der
  Oberfläche.

![JellyVault, Titelseite](docs/screenshots/title.jpg)

![JellyVault, Anfrage für eine Serie](docs/screenshots/request.jpg)

| | |
|---|---|
| ![Because you watched](docs/screenshots/because-you-watched.jpg) | ![Suche](docs/screenshots/search.jpg) |
| Empfehlungen anhand deiner gesehenen Titel | Suche in deiner Bibliothek und auf TMDB (✓ = in deiner Bibliothek) |
| ![Studios](docs/screenshots/studios.jpg) | ![Genres](docs/screenshots/genres.jpg) |
| Studios & Sagas | Genres |

## Datenschutz

Alles bleibt auf dem Fernseher. Dein TMDB-Schlüssel sowie die Adressen, Schlüssel und Passwörter von Radarr/Sonarr
werden nur auf dem Gerät gespeichert. JellyVault kommuniziert mit deinem Jellyfin-Server, TMDB, deinen
Radarr/Sonarr-Servern, einer öffentlichen wöchentlichen Top-10-Liste und GitHub, um nach Updates zu suchen. Keine Analyse-Tools:
Nach einem Absturz wird der Bericht nur gesendet, wenn du zustimmst, und nur an deinen eigenen Jellyfin-Server.

## Installation

Lade die APK von [Releases](https://github.com/romainio/JellyVault/releases) herunter, sobald ein Release veröffentlicht ist, und installiere sie auf deinem Android TV oder Fire
TV (erlaube zuerst die Installation von Apps aus unbekannten Quellen). Einstellungen und Anmeldung bleiben bei Updates
erhalten, die über eine mit demselben Schlüssel signierte App installiert werden.

Voraussetzungen: Android TV oder Fire TV mit Android 6.0 oder neuer, ein Jellyfin-Server `10.10.x` oder `10.11.x`. Ein
kostenloser [TMDB-API-Schlüssel](https://www.themoviedb.org/settings/api) zum Entdecken und Radarr/Sonarr, wenn du
Anfragen stellen möchtest.

Folge dann der [Anleitung](docs/help/de.md): Jellyfin-Anmeldung, TMDB-Schlüssel unter
*Einstellungen › TMDB & IMDb ratings* und die Einträge *Radarr* und *Sonarr* in den Einstellungen.

## Bekannte Einschränkungen

- Die Beschreibungen der Titel, die du besitzt, stammen von deinem Jellyfin-Server, und zwar in der
  *Preferred metadata language* (bevorzugte Metadatensprache), die auf dem Server für jede Bibliothek festgelegt ist.
  TMDB-Texte folgen der Sprache der Oberfläche.
- Das Entdecken verwendet Frankreich als Region: Trend- und Veröffentlichungslisten, Altersfreigaben, die Regale mit
  "französischen Filmen" und die wöchentliche Top 10.
- Die JellyVault-spezifischen Bildschirme sind nur auf Englisch und Französisch übersetzt. In anderen
  Oberflächensprachen zeigen diese Bildschirme Englisch.

## Roadmap

- **Releases auf GitHub** mit signierten APKs und In-App-Updates, die von GitHub bereitgestellt werden.
- **Entdecken-Region** nach dem Land des Fernsehers statt Frankreich.
- **Mehr Sprachen** für die JellyVault-Bildschirme.

## Bauen

```
./gradlew :app:assembleDefaultDebug
./gradlew :app:testDefaultDebugUnitTest
```

JDK 21 (das in Android Studio enthaltene JBR funktioniert) und das Android SDK. Um einen Standard-TMDB-Schlüssel in
deine eigenen Builds einzubauen, füge `tmdb.readToken=<your TMDB read access token>` zu `local.properties` hinzu (wird
nicht versioniert: committe niemals dein Token). Siehe [DEVELOPMENT.md](DEVELOPMENT.md) für die Projektstruktur und
[Intents.md](Intents.md) für die Intent-API.

## Feedback

Fehler und Ideen sind in den [Issues](https://github.com/romainio/JellyVault/issues) willkommen. Lies [CONTRIBUTING.md](CONTRIBUTING.md), bevor du einen Pull
Request öffnest.

## Danksagungen

- [Wholphin](https://github.com/damontecres/Wholphin) von damontecres: JellyVault begann als Fork davon, und der
  Großteil des Jellyfin-Client-Codes stammt daraus. Seine GPL-2.0-Lizenz gilt.
- [Jellyfin](https://jellyfin.org) und die offizielle [Jellyfin-Android-TV-App](https://github.com/jellyfin/jellyfin-androidtv),
  deren Code für das Direct-Play-Profil des Geräts angepasst wurde.
- [TMDB](https://www.themoviedb.org) für Titel, Bilder und Metadaten. This product uses the TMDB API but is not
  endorsed or certified by TMDB. (Dieses Produkt nutzt die TMDB-API, wird aber von TMDB weder unterstützt noch
  zertifiziert.)
- [Radarr](https://radarr.video) und [Sonarr](https://sonarr.tv) für Film- und Serienanfragen.

## Lizenz

GPL-2.0, siehe [LICENSE](LICENSE). Der Code ist eine Modifikation von Wholphin von damontecres (GPL-2.0); Änderungen
seit 2026.

Jellyfin, TMDB, Android TV und Fire TV sind Marken ihrer jeweiligen Inhaber; Namen, Logos und Poster in den
Screenshots gehören ihren Inhabern. JellyVault ist ein unabhängiges Projekt und steht in keiner Verbindung zu ihnen und
wird von ihnen nicht unterstützt.
