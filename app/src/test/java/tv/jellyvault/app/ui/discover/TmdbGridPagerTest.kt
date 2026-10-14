package tv.jellyvault.app.ui.discover

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.MediaCard
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbGridPagerTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val catalog = mockk<CatalogRepository>()

    private fun cards(
        page: Int,
        n: Int = 20,
    ) = List(n) { i -> MediaCard.Remote(TmdbItem(page * 100 + i, TmdbMediaType.MOVIE, "T", null, "/p.jpg", null, null, null, emptyList())) }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        JvDispatchers.configure(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        JvDispatchers.reset()
    }

    private fun TestScope.pager(
        genre: CatalogGenre,
        type: TmdbMediaType,
        maxPages: Int? = null,
    ) = TmdbGridPager(backgroundScope) { page -> catalog.genrePage(genre, type, page) }
        .also { pager -> maxPages?.let { pager.maxPages = it } }
        .also { it.start() }

    @Test
    fun first_page_loads_on_init() =
        runTest(dispatcher) {
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 1) } returns (cards(1) to 3)
            val vm = pager(CatalogGenre.ACTION, TmdbMediaType.MOVIE)
            assertEquals(20, vm.items.value.size)
        }

    @Test
    fun pages_load_once_and_stop_at_total() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 1) } returns (cards(1) to 2)
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 2) } coAnswers {
                gate.await()
                cards(2) to 2
            }
            val vm = pager(CatalogGenre.ACTION, TmdbMediaType.MOVIE)
            vm.onPosition(15)
            vm.onPosition(16)
            gate.complete(Unit)
            assertEquals(40, vm.items.value.size)
            vm.onPosition(39)
            coVerify(exactly = 1) { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 2) }
            coVerify(exactly = 0) { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 3) }
        }

    @Test
    fun early_positions_do_not_load_more() =
        runTest(dispatcher) {
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 1) } returns (cards(1) to 5)
            val vm = pager(CatalogGenre.ACTION, TmdbMediaType.MOVIE)
            vm.onPosition(3)
            coVerify(exactly = 0) { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 2) }
        }

    @Test
    fun error_keeps_loaded_items() =
        runTest(dispatcher) {
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 1) } returns (cards(1) to 3)
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 2) } throws TmdbException("down")
            val vm = pager(CatalogGenre.ACTION, TmdbMediaType.MOVIE)
            vm.onPosition(19)
            assertEquals(20, vm.items.value.size)
            assertNotNull(vm.error.value)
        }

    @Test
    fun empty_first_page_is_reported_as_empty() =
        runTest(dispatcher) {
            coEvery { catalog.genrePage(CatalogGenre.WESTERN, TmdbMediaType.MOVIE, 1) } returns (emptyList<MediaCard>() to 0)
            val vm = pager(CatalogGenre.WESTERN, TmdbMediaType.MOVIE)
            assertEquals(true, vm.empty.value)
            assertEquals(false, vm.loading.value)
        }

    @Test
    fun retry_after_error_loads_the_page() =
        runTest(dispatcher) {
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 1) } throws TmdbException("down")
            val vm = pager(CatalogGenre.ACTION, TmdbMediaType.MOVIE)
            assertNotNull(vm.error.value)
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 1) } returns (cards(1) to 3)
            vm.retry()
            assertEquals(20, vm.items.value.size)
            assertEquals(null, vm.error.value)
        }

    @Test
    fun duplicates_across_pages_are_dropped() =
        runTest(dispatcher) {
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 1) } returns (cards(1) to 2)
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 2) } returns ((cards(1, 5) + cards(2, 5)) to 2)
            val vm = pager(CatalogGenre.ACTION, TmdbMediaType.MOVIE)
            vm.onPosition(19)
            assertEquals(25, vm.items.value.size)
            assertEquals(
                25,
                vm.items.value
                    .map { it.gridId }
                    .toSet()
                    .size,
            )
        }

    @Test
    fun paging_stops_at_the_page_cap_even_if_tmdb_has_more() =
        runTest(dispatcher) {
            coEvery { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, any()) } answers { cards(thirdArg()) to 1000 }
            val vm = pager(CatalogGenre.ACTION, TmdbMediaType.MOVIE, maxPages = 2)
            vm.onPosition(19)
            vm.onPosition(39)
            assertEquals(40, vm.items.value.size)
            coVerify(exactly = 0) { catalog.genrePage(CatalogGenre.ACTION, TmdbMediaType.MOVIE, 3) }
        }
}
