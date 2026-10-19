package tv.jellyvault.app.catalog

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.library.LibraryIndex
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbResultPage

class SearchVariantsTest {
    private val tmdb = mockk<TmdbClient>()
    private val index = mockk<LibraryIndex>().also { coEvery { it.awaitIndex(any()) } returns emptyMap() }
    private val repo = CatalogRepository(tmdb, index, mockk(relaxed = true), mockk(relaxed = true))

    private fun page(vararg items: TmdbItem) = TmdbResultPage(items.toList(), 1, 1)

    private fun item(
        id: Int,
        title: String,
        type: TmdbMediaType = TmdbMediaType.TV,
    ) = TmdbItem(id, type, title, null, "/p.jpg", null, "2011-04-17", 8.5, emptyList())

    @Test
    fun variants_drop_plural_s_then_keep_the_longest_word() {
        assertEquals(listOf("game of throne", "thrones"), searchVariants("games of thrones"))
        assertEquals(listOf("breaking bad", "breaking"), searchVariants("breaking bads"))
        // Nothing to try: one word without a plural
        assertTrue(searchVariants("breaking").isEmpty())
        // A double s is not a plural, short words stay
        assertTrue(searchVariants("boss").isEmpty())
        assertTrue(searchVariants("  ").isEmpty())
    }

    @Test
    fun a_typo_that_finds_nothing_is_searched_again_with_variants() =
        runTest {
            coEvery { tmdb.search(any()) } returns page()
            coEvery { tmdb.search("game of throne") } returns page(item(1399, "Game of Thrones"))
            val cards = repo.searchCards("games of thrones")
            assertEquals(listOf(1399), cards.map { it.tmdb!!.tmdbId })
        }

    @Test
    fun a_query_with_results_is_not_searched_again() =
        runTest {
            coEvery { tmdb.search("game of thrones") } returns page(item(1399, "Game of Thrones"), item(2, "B"), item(3, "C"))
            assertEquals(3, repo.searchCards("game of thrones").size)
        }
}
