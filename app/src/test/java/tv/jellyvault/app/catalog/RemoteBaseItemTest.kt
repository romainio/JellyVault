package tv.jellyvault.app.catalog

import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.ui.nav.Destination

class RemoteBaseItemTest {
    private fun tmdb(
        id: Int,
        type: TmdbMediaType,
    ) = TmdbItem(id, type, "Titre $id", "Résumé", "/p$id.jpg", "/b$id.jpg", "2020-05-01", 7.5, listOf(28))

    @Test
    fun remote_item_navigates_to_tmdb_detail_and_uses_tmdb_images() {
        val item = tmdb(10, TmdbMediaType.MOVIE)
        val base = item.toRemoteBaseItem()

        assertTrue(base.isRemote)
        assertFalse(base.inLibraryBadge)
        assertEquals(Destination.TmdbDetail(item), base.destination())
        assertEquals(item.posterUrl, base.imageUrlOverride)
        assertEquals(item.backdropUrl, base.backdropUrlOverride)
        assertEquals(BaseItemKind.MOVIE, base.type)
        assertEquals("Titre 10", base.name)
        assertEquals(2020, base.data.productionYear)
        assertEquals("10", base.data.providerIds?.get("Tmdb"))
    }

    @Test
    fun series_map_to_series_kind() {
        assertEquals(BaseItemKind.SERIES, tmdb(10, TmdbMediaType.TV).toRemoteBaseItem().type)
    }

    @Test
    fun synthetic_ids_are_stable_and_distinct_per_type() {
        val movie = tmdb(10, TmdbMediaType.MOVIE)
        assertEquals(movie.toRemoteBaseItem().id, movie.toRemoteBaseItem().id)
        assertNotEquals(movie.toRemoteBaseItem().id, tmdb(10, TmdbMediaType.TV).toRemoteBaseItem().id)
    }

    @Test
    fun remote_movie_is_not_playable() {
        val base = tmdb(10, TmdbMediaType.MOVIE).toRemoteBaseItem()
        assertEquals(BaseItemKind.MOVIE, base.type)
        assertFalse(base.playable)
    }

    @Test
    fun local_movie_stays_playable() {
        val base = tmdb(10, TmdbMediaType.MOVIE).toRemoteBaseItem().copy(destinationOverride = null)
        assertFalse(base.isRemote)
        assertTrue(base.playable)
    }
}
