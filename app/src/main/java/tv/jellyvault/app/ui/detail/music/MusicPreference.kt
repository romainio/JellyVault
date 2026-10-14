package tv.jellyvault.app.ui.detail.music

import tv.jellyvault.app.R
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.preferences.AppSwitchPreference
import tv.jellyvault.app.preferences.updateMusicPreferences

fun getMusicPreferences() =
    listOf(
        AppSwitchPreference<AppPreferences>(
            title = R.string.show_album_cover,
            defaultValue = false,
            getter = { it.musicPreferences.showAlbumArt },
            setter = { prefs, value ->
                prefs.updateMusicPreferences { showAlbumArt = value }
            },
        ),
        AppSwitchPreference<AppPreferences>(
            title = R.string.show_visualizer,
            defaultValue = false,
            getter = { it.musicPreferences.showVisualizer },
            setter = { prefs, value ->
                prefs.updateMusicPreferences { showVisualizer = value }
            },
        ),
        AppSwitchPreference<AppPreferences>(
            title = R.string.show_backdrop,
            defaultValue = false,
            getter = { it.musicPreferences.showBackdrop },
            setter = { prefs, value ->
                prefs.updateMusicPreferences { showBackdrop = value }
            },
        ),
        AppSwitchPreference<AppPreferences>(
            title = R.string.show_lyrics,
            defaultValue = false,
            getter = { it.musicPreferences.showLyrics },
            setter = { prefs, value ->
                prefs.updateMusicPreferences { showLyrics = value }
            },
        ),
    )
