package tv.jellyvault.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.toRemoteBaseItem
import tv.jellyvault.app.ui.util.EmptyStringProvider
import tv.jellyvault.app.util.HomeRowLoadingState

class FirstFocusableRowTest {
    private val item =
        TmdbItem(1, TmdbMediaType.MOVIE, "T", null, "/p.jpg", null, null, null, emptyList()).toRemoteBaseItem()
    private val loaded = HomeRowLoadingState.Success(EmptyStringProvider, listOf(item))
    private val empty = HomeRowLoadingState.Success(EmptyStringProvider, emptyList())
    private val pending = HomeRowLoadingState.Pending(EmptyStringProvider)
    private val error = HomeRowLoadingState.Error(EmptyStringProvider, message = "x")

    @Test
    fun waits_while_an_earlier_row_is_still_loading() {
        assertNull(firstFocusableRowIndex(listOf(pending, loaded)))
    }

    @Test
    fun focuses_the_top_row_once_it_is_loaded_even_if_later_rows_are_pending() {
        assertEquals(0, firstFocusableRowIndex(listOf(loaded, pending, pending)))
    }

    @Test
    fun skips_empty_and_failed_rows_above() {
        assertEquals(2, firstFocusableRowIndex(listOf(empty, error, loaded, pending)))
    }

    @Test
    fun nothing_to_focus_when_all_rows_are_empty() {
        assertNull(firstFocusableRowIndex(listOf(empty, error)))
    }
}
