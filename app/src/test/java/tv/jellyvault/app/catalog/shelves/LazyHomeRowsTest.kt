package tv.jellyvault.app.catalog.shelves

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.services.HomeRowConfigDisplay
import tv.jellyvault.app.ui.util.ResStringProvider

class LazyHomeRowsTest {
    private fun rows(vararg configs: HomeRowConfig) =
        configs.mapIndexed { i, c -> HomeRowConfigDisplay(i, ResStringProvider(R.string.app_name), c) }

    private val trend = HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE)
    private val cont = HomeRowConfig.ContinueWatchingCombined()

    private fun shelf(id: String) = HomeRowConfig.TmdbShelf(id)

    @Test
    fun only_the_styles_after_the_first_ones_wait_for_the_scroll() {
        val home =
            rows(
                trend,
                cont,
                shelf("a"),
                shelf("b"),
                shelf("c"),
                shelf("d"),
                shelf("e"),
                HomeRowConfig.RecentlyAdded(java.util.UUID.randomUUID()),
            )
        assertEquals(setOf(5, 6), onDemandRows(home, eagerShelves = 3))
    }

    @Test
    fun rows_are_due_a_few_rows_before_they_show() {
        assertEquals(setOf(5, 6), dueRows(setOf(5, 6, 9), lastVisible = 3, ahead = 3))
        assertEquals(emptySet<Int>(), dueRows(setOf(9), lastVisible = 3, ahead = 3))
    }
}
