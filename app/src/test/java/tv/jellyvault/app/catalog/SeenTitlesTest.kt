package tv.jellyvault.app.catalog

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.library.LibraryItemSource
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.BaseItem
import java.util.UUID

class SeenTitlesTest {
    private fun item(id: Int) = TmdbItem(id, TmdbMediaType.MOVIE, "T$id", null, "/p.jpg", null, null, 7.0, emptyList())

    @Test
    fun titles_the_user_rated_show_as_seen_in_catalog_rows() =
        runTest {
            val index = mockk<LibraryIndex>()
            coEvery { index.awaitIndex(any()) } returns emptyMap()
            val seen = SeenTitles { setOf(TmdbKey(TmdbMediaType.MOVIE, 1)) }
            val repo = CatalogRepository(mockk(), index, mockk<LibraryItemSource>(relaxed = true), mockk(relaxed = true), seen)
            val items = repo.cards(listOf(item(1), item(2))).map { it.toBaseItem() }
            assertEquals(listOf(true, false), items.map { it.played })
        }

    @Test
    fun an_owned_title_shows_one_badge_seen_wins_over_the_library_check() {
        fun owned(
            played: Boolean,
            seenOnImdb: Boolean,
        ) = BaseItem(
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.MOVIE,
                userData =
                    UserItemDataDto(
                        playbackPositionTicks = 0,
                        playCount = 0,
                        isFavorite = false,
                        played = played,
                        key = "k",
                        itemId = UUID.randomUUID(),
                    ),
            ),
            inLibraryBadge = true,
            seenOnImdb = seenOnImdb,
        )
        // (seen, library check) for: not seen, played on Jellyfin, rated on IMDb only
        assertEquals(
            listOf(false to true, true to false, true to false),
            listOf(owned(false, false), owned(true, false), owned(false, true)).map { it.seen to it.showLibraryCheck },
        )
    }
}
