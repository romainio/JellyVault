package tv.jellyvault.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbLanguage
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.tmdbGenreName
import java.util.Locale

private val FR = TmdbLanguage.of(Locale.FRANCE)

class RemoteGenresTest {
    @Test
    fun genre_ids_have_french_names_per_media_type() {
        assertEquals("Horreur", tmdbGenreName(27, TmdbMediaType.MOVIE, FR))
        assertEquals("Science-Fiction", tmdbGenreName(878, TmdbMediaType.MOVIE, FR))
        assertEquals("Action & Aventure", tmdbGenreName(10759, TmdbMediaType.TV, FR))
        assertNull(tmdbGenreName(10759, TmdbMediaType.MOVIE, FR))
    }

    @Test
    fun genre_ids_have_english_names_outside_a_french_interface() {
        assertEquals("Horror", tmdbGenreName(27, TmdbMediaType.MOVIE, TmdbLanguage.of(Locale.US)))
        assertEquals("Sci-Fi & Fantasy", tmdbGenreName(10765, TmdbMediaType.TV, TmdbLanguage.of(Locale.GERMANY)))
        assertNull(tmdbGenreName(10759, TmdbMediaType.MOVIE, TmdbLanguage.of(Locale.US)))
    }

    @Test
    fun remote_items_carry_their_genres_and_keep_a_synthetic_id() {
        val item = TmdbItem(1, TmdbMediaType.MOVIE, "Resident Evil", null, "/p.jpg", null, "2026-09-01", 7.3, listOf(27, 878, 999))
        val base = item.toRemoteBaseItem(language = FR)
        assertEquals(listOf("Horreur", "Science-Fiction"), base.data.genres)
        assertEquals(java.util.UUID.nameUUIDFromBytes("tmdb:movie:1".toByteArray()), base.id)
    }
}
