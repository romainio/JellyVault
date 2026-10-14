package tv.jellyvault.app.catalog.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainstreamTest {
    private fun item(
        language: String?,
        votes: Int = 0,
        popularity: Double = 0.0,
        genres: List<Int> = emptyList(),
    ) = TmdbItem(1, TmdbMediaType.MOVIE, "T", null, "/p.jpg", null, "2020-01-01", 7.0, genres, language, votes, popularity)

    @Test
    fun french_and_english_titles_always_pass() {
        assertTrue(item("fr").isMainstream())
        assertTrue(item("en").isMainstream())
        assertTrue(item(null).isMainstream())
    }

    @Test
    fun other_languages_need_a_real_success() {
        assertFalse(item("ko", votes = 120, popularity = 30.0).isMainstream())
        assertFalse(item("es", votes = 400, popularity = 60.0).isMainstream())
        assertTrue(item("ko", votes = 6000).isMainstream())
        assertTrue(item("es", votes = 50, popularity = 400.0).isMainstream())
    }

    @Test
    fun animation_passes_in_any_language() {
        assertTrue(item("ja", genres = listOf(16)).isMainstream())
    }

    @Test
    fun filter_keeps_order_and_drops_obscure_titles() {
        val items = listOf(item("fr"), item("ko", votes = 10), item("en"))
        assertEquals(listOf("fr", "en"), items.mainstream().map { it.originalLanguage })
    }
}
