package tv.jellyvault.app.ui.detail.tmdb

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.tmdb.TmdbCastMember
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbNamed

class TmdbMoreMenuTest {
    private fun details(
        type: TmdbMediaType,
        cast: List<TmdbCastMember> = emptyList(),
        collection: TmdbNamed? = null,
        studio: TmdbNamed? = null,
        network: TmdbNamed? = null,
    ) = TmdbDetails(
        item = TmdbItem(1, type, "T", null, "/p.jpg", null, null, null, emptyList()),
        runtimeMinutes = null,
        seasons = emptyList(),
        genres = emptyList(),
        tagline = null,
        certification = null,
        logoUrl = null,
        trailerKey = null,
        recommendations = emptyList(),
        tvdbId = null,
        cast = cast,
        collection = collection,
        studio = studio,
        network = network,
    )

    @Test
    fun movie_with_everything() {
        val entries =
            tmdbMoreEntries(
                details(
                    TmdbMediaType.MOVIE,
                    cast = listOf(TmdbCastMember("Tom Hanks", "Woody", null)),
                    collection = TmdbNamed(10194, "Toy Story - Saga"),
                    studio = TmdbNamed(3, "Pixar"),
                ),
            )
        assertEquals(
            listOf(
                TmdbMoreEntry.Cast,
                TmdbMoreEntry.Saga(BrowseTarget.AnyCollection(10194, "Toy Story - Saga")),
                TmdbMoreEntry.Studio(BrowseTarget.AnyCompany(3, "Pixar")),
                TmdbMoreEntry.Info,
            ),
            entries,
        )
    }

    @Test
    fun series_use_the_network_and_empty_parts_are_hidden() {
        assertEquals(
            listOf(TmdbMoreEntry.Studio(BrowseTarget.AnyNetwork(49, "HBO")), TmdbMoreEntry.Info),
            tmdbMoreEntries(details(TmdbMediaType.TV, network = TmdbNamed(49, "HBO"), studio = TmdbNamed(7, "X"))),
        )
        assertEquals(listOf(TmdbMoreEntry.Info), tmdbMoreEntries(details(TmdbMediaType.MOVIE)))
    }

    @Test
    fun owned_titles_get_the_full_jellyfin_page_entry_last() {
        val entries = tmdbMoreEntries(details(TmdbMediaType.MOVIE), owned = true)
        assertEquals(listOf(TmdbMoreEntry.Info, TmdbMoreEntry.JellyfinPage), entries)
        assertEquals(listOf(TmdbMoreEntry.Info), tmdbMoreEntries(details(TmdbMediaType.MOVIE), owned = false))
    }
}
