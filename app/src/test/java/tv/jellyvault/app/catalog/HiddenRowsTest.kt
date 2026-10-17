package tv.jellyvault.app.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.preferences.CatalogPreferences
import java.util.UUID

class HiddenRowsTest {
    private val parent = UUID.randomUUID()

    @Test
    fun nothing_is_hidden_by_default() {
        val hidden = CatalogPreferences.getDefaultInstance().hiddenRows()
        assertFalse(HomeRowConfig.ContinueWatching().isHiddenBy(hidden))
        assertFalse(HomeRowConfig.RecentlyAdded(parent).isHiddenBy(hidden))
    }

    @Test
    fun continue_watching_hides_the_resume_rows_only() {
        val hidden = HiddenRows(continueWatching = true)
        assertTrue(HomeRowConfig.ContinueWatching().isHiddenBy(hidden))
        assertTrue(HomeRowConfig.ContinueWatchingCombined().isHiddenBy(hidden))
        assertFalse(HomeRowConfig.NextUp().isHiddenBy(hidden))
        assertFalse(HomeRowConfig.RecentlyAdded(parent).isHiddenBy(hidden))
    }

    @Test
    fun recently_added_hides_every_library_recently_added_row() {
        val hidden = HiddenRows(recentlyAdded = true)
        assertTrue(HomeRowConfig.RecentlyAdded(parent).isHiddenBy(hidden))
        assertFalse(HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE).isHiddenBy(hidden))
        assertFalse(HomeRowConfig.ContinueWatching().isHiddenBy(hidden))
    }
}
