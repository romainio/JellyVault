package tv.jellyvault.app.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepeatTest {
    @Test
    fun coming_back_with_the_same_query_does_not_search_again() {
        // The page still passes the old "combined" preference; it must not matter
        assertTrue(isRepeatedSearch(current = "dune", query = "dune", force = false))
    }

    @Test
    fun a_new_query_or_a_forced_refresh_searches() {
        assertFalse(isRepeatedSearch(current = "dune", query = "dune 2", force = false))
        assertFalse(isRepeatedSearch(current = "dune", query = "dune", force = true))
        assertFalse(isRepeatedSearch(current = null, query = "dune", force = false))
    }
}
