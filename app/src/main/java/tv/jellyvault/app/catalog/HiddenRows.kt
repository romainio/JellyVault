package tv.jellyvault.app.catalog

import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.preferences.CatalogPreferences

/**
 * Rows the user hid everywhere in Settings › Advanced › Catalog
 */
data class HiddenRows(
    val continueWatching: Boolean = false,
    val recentlyAdded: Boolean = false,
) {
    companion object {
        /** Set from the preferences as soon as they load, for the pages that build their rows without them */
        @Volatile
        var current: HiddenRows = HiddenRows()
    }
}

fun CatalogPreferences.hiddenRows() = HiddenRows(continueWatching = hideContinueWatching, recentlyAdded = hideRecentlyAdded)

/**
 * Whether a home row is hidden: "Continue watching" (alone or with next up) and "Recently added" of any library
 */
fun HomeRowConfig.isHiddenBy(hidden: HiddenRows): Boolean =
    when (this) {
        is HomeRowConfig.ContinueWatching,
        is HomeRowConfig.ContinueWatchingCombined,
        -> hidden.continueWatching

        is HomeRowConfig.RecentlyAdded -> hidden.recentlyAdded

        else -> false
    }
