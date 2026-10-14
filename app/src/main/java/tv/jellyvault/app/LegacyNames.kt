package tv.jellyvault.app

/**
 * Names inherited from Wholphin, the project JellyVault started from, that are stored outside the code: renaming
 * them would lose data on devices that already run the app.
 */
object LegacyNames {
    /** Room database file (`databases/wholphin`) */
    const val DATABASE = "wholphin"

    /** Jellyfin display-preferences client: the home layout and removed Next Up are saved on the server under it */
    const val DISPLAY_PREFS_CLIENT = "Wholphin"
    const val DISPLAY_PREFS_CLIENT_DEBUG = "Wholphin (Debug)"

    /** Unique WorkManager names of builds before 1.1.0: their records name worker classes that no longer exist */
    val WORK_NAMES =
        listOf(
            "com.github.damontecres.wholphin.services.SuggestionsWorker",
            "com.github.damontecres.wholphin.services.LatestNextUpWorker",
            "com.github.damontecres.wholphin.services.tvprovider.TvProviderWorker",
        )

    /** Update URL saved in the preferences of builds before 1.1.0 */
    const val UPDATE_URL = "https://api.github.com/repos/damontecres/Wholphin/releases/latest"

    /** Update URL of the JellyVault repository under any owner name: the repository owner was renamed once already */
    val JELLYVAULT_UPDATE_URL = Regex("https://api\\.github\\.com/repos/[^/]+/JellyVault/releases/latest")
}
