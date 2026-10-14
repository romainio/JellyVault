# JellyVault developer's guide

See also the [Contributing](CONTRIBUTING.md) guide for general information on contributing to the project.

##  Overview

This project is an Android TV client for Jellyfin. It is written in Kotlin and uses the official [Jellyfin Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin) to interact with the server.

The app is a single Activity (`MainActivity`) with MVVM architecture.

The app uses:
* [Compose](https://developer.android.com/jetpack/compose) for the UI
* [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) for navigating app screen
* [Room](https://developer.android.com/training/data-storage/room) & [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) for local data storage
* [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) for dependency injection
* [Media3/ExoPlayer](https://developer.android.com/media/media3/exoplayer) for media playback
* [MPV/libmpv](https://github.com/mpv-player/mpv) is not bundled (see the `mpv-stub` module): playback uses ExoPlayer
* [Coil](https://coil-kt.github.io/coil/) for image loading
* [OkHttp](https://square.github.io/okhttp/) for HTTP requests

## Getting started

We follow GitHub's fork & pull request model for contributions.

After forking and cloning your fork, you can import the project into Android Studio.

### Development environment

Building needs JDK 21 (what the CI uses; Android Studio's bundled JBR works) and the Android SDK. The main Gradle tasks are:

```
./gradlew :app:assembleDefaultDebug
./gradlew :app:testDefaultDebugUnitTest
```

The app has three flavors (`default`, `appstore`, `firetv`); the `default` one is what the tasks above build. The package is `tv.jellyvault.app` (`tv.jellyvault.app.debug` for debug builds).

It is recommended to use a recent version of [Android Studio](https://developer.android.com/studio). Make sure the [version is compatible](https://developer.android.com/build/releases/gradle-plugin#android_gradle_plugin_and_android_studio_compatibility) with the Android Gradle Plugin version in [`gradle/libs.versions.toml`](./gradle/libs.versions.toml).

Code formatting should follow [ktlint's](https://github.com/pinterest/ktlint) rules. Find the `ktlint` version in [`.pre-commit-config.yaml`](./.pre-commit-config.yaml). Optionally, install the [ktlint plugin](https://plugins.jetbrains.com/plugin/15057-ktlint) in Android Studio to run automatically. Configure the version in `Settings->Tools->KtLint->Ruleset Version`.

Also, it's recommend to add an extra ruleset jar for Compose-specific KtLint: https://mrmans0n.github.io/compose-rules/ktlint/#using-with-ktlint-cli-or-the-ktlint-intellij-plugin

Also setup [pre-commit](https://github.com/pre-commit/pre-commit) which will run `ktlint` as well on each commit, plus check for other common issues.

#### Extensions

JellyVault can use the Media3 ffmpeg/av1 decoders for extra playback compatibility. They are not required to build the app: without them some codecs will not play. To include them in a local build, place the built `lib-decoder-ffmpeg-release.aar` and/or `lib-decoder-av1-release.aar` in `app/libs`; the Gradle build picks them up automatically.

libMPV is not bundled: the `mpv-stub` module keeps the MPV backend compiling, and playback falls back to ExoPlayer.

#### Release signing

Pushing a `v*` tag runs `.github/workflows/release.yml`, which signs the release APKs with four repository secrets: `SIGNING_KEY` (the keystore, base64 without line breaks), `KEY_ALIAS`, `KEY_PASSWORD` and `KEY_STORE_PASSWORD`. Without them the build is unsigned and the signature check fails the run.

To sign a local release build with the same key, set the same variables, with `KEY_STORE_FILE` pointing at the keystore instead of `SIGNING_KEY`:

    KEY_STORE_FILE=/path/to/release.p12 KEY_ALIAS=... KEY_PASSWORD=... KEY_STORE_PASSWORD=... ./gradlew :app:assembleDefaultRelease

Otherwise `release.signing.config=debug` in `local.properties` signs local release builds with the debug key. Android refuses an update signed with a different key than the installed app, so keep one key per device.

## Code organization

Code is split into several packages:
- `catalog` - TMDB discovery, recommendations, and the Radarr/Sonarr request clients
- `data` - app-specific data models and services
- `preferences` - Non-UI related code for user settings and preferences
- `services` - hilt injectable services often used by ViewModels for API calls
- `ui` - User interface code and ViewModels
- `util` - Utility classes and functions

### Settings

There are a few different ways user settings are stored:
1. `AppPreferences` via DataStore
2. Room database
3. Key-Value DataStore

#### App preferences

These are generally settings that apply across the whole app regardless of the currently active user.

The `AppPreferences` object can be retrieved from the `UserPreferencesService` or directly via injecting `DataStore<AppPreferences>`.

The `AppPreference` (note the `s` differences) objects are used to create the UI for configuring settings using the composable functions in `tv.jellyvault.app.ui.preferences`.

##### How to add a new app setting

1. Add entry in `app/src/main/proto/AppPreferencesStore.proto` & build to generate classes
2. Add new `AppPreference` object in `AppPreference.kt`
3. Add new object to a `PreferenceGroup` (listed in `AppPreference.kt`)
4. Update `AppPreferencesSerializer` to set the default value for new installs
5. If needed, update `AppUpgradeHandler` to set the default value for app upgrades
    - Since preferences use proto3, the [default values](https://protobuf.dev/programming-guides/proto3/#default) are zero, false, or the first enum, so only need this step if the default value is different

#### Room settings

These are settings were generally are applied per user. They are stored in several different tables. The entities are defined in `tv.jellyvault.app.data.model`.

Additionally, all Jellyfin server and user info is stored in Room tables. Radarr/Sonarr settings are stored in a dedicated DataStore (`arr_settings.pb`), excluded from Android backups.

Some examples are:
- `JellyfinServer`
- `JellyfinUser`
- `LibraryDisplayInfo`
- `ItemPlayback`

Some of the models, such as `GetItemsFilter` or `ViewOptions`, are stored as JSON in a column instead of defined entities. This should be used sparingly.

#### Key-value settings

These settings are similar to Room ones and are usually stored per user. These can be accessed via `KeyValueService`. The values are `@Serializable` objects saved as JSON.

This should be used sparingly.

#### Experimental

Some settings are considered "experimental". These may cause instabilities or may need further testing. They may be changed or removed any time.

These are stored in the `ExperimentalPreferences` object in `AppPreferences`. Accessing them should always be gated with `ExperimentalPreferences.enabled`. There are a few helper functions for this purpose.

## TMDB

JellyVault reads trends, genres and details from TMDB. Users enter their own key in *Settings › TMDB & IMDb ratings › TMDB API key* (a v3 API key or a v4 read access token, stored on the device only). Get a free key at <https://www.themoviedb.org/settings/api>.

To bake a default key into your own builds, add your v4 read access token to `local.properties` (not tracked, never commit it):

    tmdb.readToken=<your TMDB read access token>

A key entered in the app takes precedence over the built-in one. Without any key, the TMDB rows are hidden and the app works as a plain Jellyfin client.

Any use of TMDB data must keep the attribution shown in the About dialog: "This product uses the TMDB API but is not endorsed or certified by TMDB."

## Radarr and Sonarr

Requests go straight to Radarr and Sonarr from the app. Their URL, API key and optional HTTP basic auth credentials are entered in the settings and stored in a dedicated DataStore (see above).

## Gathering app logs

When reporting a bug, include the logs. With the TV connected over [ADB](https://developer.android.com/studio/command-line/adb):

```
adb logcat -d > jellyvault-log.txt
```

Reproduce the problem first, then run the command right away so the log still holds it. For a crash, `adb logcat -b crash -d` shows the stack trace. The app can also send its logs to your Jellyfin server from the error screen (*Send logs*). Review the logs for personal information before sharing them publicly.

## Legacy names

For upgrades from builds made before the package rename, the Room database file name, the Jellyfin display preferences client name and the old background worker names are kept in `LegacyNames.kt`. Do not change them: doing so would lose existing users' data.
