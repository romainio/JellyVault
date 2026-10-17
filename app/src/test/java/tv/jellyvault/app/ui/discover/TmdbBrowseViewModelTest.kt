package tv.jellyvault.app.ui.discover

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.CatalogSaga
import tv.jellyvault.app.catalog.CatalogStudio
import tv.jellyvault.app.catalog.MediaCard
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.ui.util.StringStringProvider
import tv.jellyvault.app.util.HomeRowLoadingState
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbBrowseViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val catalog = mockk<CatalogRepository>()

    private fun card(id: Int) = MediaCard.Remote(TmdbItem(id, TmdbMediaType.MOVIE, "T$id", null, "/p.jpg", null, null, null, emptyList()))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        JvDispatchers.configure(dispatcher)
        coEvery { catalog.queryRow(any(), any()) } returns listOf(card(1))
        coEvery { catalog.queryPage(any(), any()) } returns (listOf(card(2)) to 1)
        coEvery { catalog.sagaRow(any()) } returns listOf(card(3), card(4))
        coEvery { catalog.collectionRow(any()) } returns listOf(card(5))
        coEvery { catalog.collectionName(any()) } returns "Toy Story - Saga"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        JvDispatchers.reset()
    }

    private fun vm() =
        TmdbBrowseViewModel(catalog, mockk(relaxed = true), mockk(relaxed = true)).also {
            it.today =
                { LocalDate.of(2026, 9, 27) }
        }

    @Test
    fun genre_page_loads_first_rows_and_the_grid() {
        val vm = vm()
        vm.init(BrowseTarget.Genre(CatalogGenre.ANIMATION, TmdbMediaType.MOVIE))
        assertEquals(9, vm.rows.value.size)
        assertEquals(5, vm.rows.value.count { it is HomeRowLoadingState.Success })
        vm.onVisibleRow(8)
        assertEquals(9, vm.rows.value.count { it is HomeRowLoadingState.Success })
        assertEquals(
            1,
            vm.grid.value!!
                .items.value.size,
        )
    }

    @Test
    fun empty_rows_are_hidden() {
        coEvery { catalog.queryRow(any(), any()) } returns emptyList()
        val vm = vm()
        vm.init(BrowseTarget.Studio(CatalogStudio.PIXAR))
        val first = vm.rows.value.first() as HomeRowLoadingState.Success
        assertEquals(0, first.items.size)
    }

    @Test
    fun saga_has_one_row_and_no_grid() {
        val vm = vm()
        vm.init(BrowseTarget.Saga(CatalogSaga.TOY_STORY))
        assertNull(vm.grid.value)
        assertEquals(2, (vm.rows.value.single() as HomeRowLoadingState.Success).items.size)
    }

    @Test
    fun init_twice_with_the_same_target_does_not_reload() {
        val vm = vm()
        val target = BrowseTarget.Studio(CatalogStudio.PIXAR)
        vm.init(target)
        vm.init(target)
        // Popular, new, top rated, classics, then the first saga
        coVerify(exactly = 4) { catalog.queryRow(any(), any()) }
        coVerify(exactly = 1) { catalog.collectionRow(10194) }
        assertNotNull(vm.grid.value)
    }

    @Test
    fun studio_saga_rows_take_the_tmdb_collection_name() {
        val vm = vm()
        vm.init(BrowseTarget.Studio(CatalogStudio.PIXAR))
        val saga = vm.rows.value[4] as HomeRowLoadingState.Success
        assertEquals(StringStringProvider("Toy Story"), saga.title)
        assertEquals(1, saga.items.size)
    }
}
