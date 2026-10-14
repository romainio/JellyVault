<p align="center"><img src="docs/assets/jellyvault-logo.png" width="128" alt="JellyVault logo"></p>
<h1 align="center">JellyVault</h1>
<p align="center">Your Jellyfin library with a streaming-service home on Android TV: TMDB discovery and Radarr/Sonarr requests from the couch.</p>
<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%20TV%20%7C%20Fire%20TV-7B4DFF" alt="Platform: Android TV and Fire TV">
  <img src="https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 6.0 or later">
  <img src="https://img.shields.io/badge/works%20with-Jellyfin%2010.10%20%7C%2010.11-00A4DC?logo=jellyfin&logoColor=white" alt="Works with Jellyfin 10.10 and 10.11">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose">
  <img src="https://img.shields.io/badge/license-GPL--2.0-2ea44f" alt="License: GPL-2.0">
</p>
<p align="center"><b>English</b> · <a href="README.fr.md">Français</a> · <a href="README.es.md">Español</a> · <a href="README.de.md">Deutsch</a></p>

> **Android TV and Fire TV only.** JellyVault needs your own [Jellyfin](https://jellyfin.org) server. TMDB discovery and
> Radarr/Sonarr requests are optional: without them it works as a plain Jellyfin client.

JellyVault turns your Jellyfin library into a streaming-service style home: trending titles, recommendations from what
you watched, studios and genres. Titles you don't have yet open on a full page, and one button sends them to Radarr or
Sonarr.

**Help:** the [user guide](docs/help/en.md) covers installation, the TMDB key, Radarr/Sonarr, requests, ratings, updates
and troubleshooting.

![JellyVault, home screen](docs/screenshots/home.jpg)

## What JellyVault does

- **Home in rows**: continue watching, trending, *Because you watched…*, *Movies for you*, your TMDB watchlist and
  themed shelves.
- **Discover pages** for movies and series: recommendations, genres, studios & sagas or networks, and trending rows per
  streaming platform.
- **Full-screen title pages** with cast, trailer and *More like this*, and playback of the copy you own.
- **Requests**: a movie to Radarr, a series, a season or all seasons to Sonarr, with the download status on the title
  page.
- **Search** across your library and TMDB at once.
- **TMDB account link**: your ratings and watchlist feed the recommendations and mark rated titles as seen.
- **Jellyfin client** features inherited from Wholphin: library views, ExoPlayer playback, subtitles, live TV, music,
  trickplay.
- **Languages**: interface in English and French; TMDB titles, synopses and genres follow the interface language.

![JellyVault, title page](docs/screenshots/title.jpg)

![JellyVault, requesting a series](docs/screenshots/request.jpg)

| | |
|---|---|
| ![Because you watched](docs/screenshots/because-you-watched.jpg) | ![Search](docs/screenshots/search.jpg) |
| Recommendations from what you watched | Search across your library and TMDB (✓ = in your library) |
| ![Studios](docs/screenshots/studios.jpg) | ![Genres](docs/screenshots/genres.jpg) |
| Studios & sagas | Genres |

## Privacy

Everything stays on the TV. Your TMDB key and your Radarr/Sonarr addresses, keys and passwords are stored on the device
only. JellyVault talks to your Jellyfin server, TMDB, your Radarr/Sonarr servers, a public weekly top 10 list,
and GitHub to check for updates. No analytics: after a crash, the report is sent only if you accept it, and only to your own
Jellyfin server.

## Installation

Download the APK from [Releases](https://github.com/romainio/JellyVault/releases) once a release is published, and install it on your Android TV or Fire
TV (allow apps from unknown sources first). Settings and sign-in survive updates installed over an app signed with the
same key.

Requirements: Android TV or Fire TV with Android 6.0 or later, a Jellyfin server `10.10.x` or `10.11.x`. A free
[TMDB API key](https://www.themoviedb.org/settings/api) for discovery, and Radarr/Sonarr if you want requests.

Then follow the [user guide](docs/help/en.md): Jellyfin sign-in, TMDB key in *Settings › TMDB & IMDb ratings*, and the
*Radarr* and *Sonarr* entries of the settings.

## Known limitations

- Descriptions of the titles you own come from your Jellyfin server, in the *Preferred metadata language* set for each
  library on the server. TMDB texts follow the interface language.
- Discovery uses France as its region: trending and release lists, age ratings, the "French films" shelves and the
  weekly top 10.
- The JellyVault-specific screens are translated in English and French only. Other interface languages show English
  on those screens.

## Roadmap

- **Releases on GitHub** with signed APKs and in-app updates served from GitHub.
- **Discovery region** following the TV's country instead of France.
- **More languages** for the JellyVault screens.

## Building

```
./gradlew :app:assembleDefaultDebug
./gradlew :app:testDefaultDebugUnitTest
```

JDK 21 (Android Studio's bundled JBR works) and the Android SDK. To bake a default TMDB key into your own builds, add
`tmdb.readToken=<your TMDB read access token>` to `local.properties` (not tracked: never commit your token). See
[DEVELOPMENT.md](DEVELOPMENT.md) for the project layout and [Intents.md](Intents.md) for the intent API.

## Feedback

Bugs and ideas are welcome in [Issues](https://github.com/romainio/JellyVault/issues). See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull
request.

## Credits

- [Wholphin](https://github.com/damontecres/Wholphin) by damontecres: JellyVault started as a fork of it, and most of
  the Jellyfin client code comes from it. Its GPL-2.0 license applies.
- [Jellyfin](https://jellyfin.org) and the official [Jellyfin Android TV app](https://github.com/jellyfin/jellyfin-androidtv),
  whose code was adapted for the device direct play profile.
- [TMDB](https://www.themoviedb.org) for titles, images and metadata. This product uses the TMDB API but is not endorsed
  or certified by TMDB.
- [Radarr](https://radarr.video) and [Sonarr](https://sonarr.tv) for movie and series requests.

## License

GPL-2.0, see [LICENSE](LICENSE). The code is modified from Wholphin by damontecres (GPL-2.0); modifications since 2026.

Jellyfin, TMDB, Android TV and Fire TV are trademarks of their respective owners; names, logos and posters in
the screenshots belong to their owners. JellyVault is an independent project, not affiliated with or endorsed by them.
