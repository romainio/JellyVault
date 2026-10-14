package tv.jellyvault.app.ui.search

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jellyvault.app.data.model.BaseItem
import java.util.UUID

class SearchFocusTargetTest {
    private val found = SearchResult.Success(listOf(BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))))
    private val empty = SearchResult.Success(emptyList())

    @Test
    fun skips_empty_results_to_reach_the_tmdb_row() {
        assertEquals(2, searchFocusTarget(listOf(empty, empty, found)))
    }

    @Test
    fun waits_while_an_earlier_row_is_searching() {
        assertNull(searchFocusTarget(listOf(SearchResult.Searching, found)))
    }

    @Test
    fun first_non_empty_result_wins() {
        assertEquals(0, searchFocusTarget(listOf(found, SearchResult.Searching, found)))
    }

    @Test
    fun nothing_found_is_null() {
        assertNull(searchFocusTarget(listOf(empty, SearchResult.NoQuery)))
    }
}
