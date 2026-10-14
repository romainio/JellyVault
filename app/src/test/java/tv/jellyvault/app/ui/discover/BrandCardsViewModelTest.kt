package tv.jellyvault.app.ui.discover

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.CatalogNetwork
import tv.jellyvault.app.catalog.CatalogSaga
import tv.jellyvault.app.catalog.CatalogStudio
import tv.jellyvault.app.catalog.tmdb.TmdbBrand
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbCollection
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset

@OptIn(ExperimentalCoroutinesApi::class)
class BrandCardsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val tmdb = mockk<TmdbClient>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        JvDispatchers.configure(dispatcher)
        coEvery { tmdb.company(any()) } returns TmdbBrand("x", "https://logo")
        coEvery { tmdb.network(any()) } returns TmdbBrand("x", "https://net")
        coEvery { tmdb.collection(any()) } returns TmdbCollection("x", "https://backdrop", emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        JvDispatchers.reset()
    }

    @Test
    fun movies_show_studios_then_sagas_with_images() {
        val vm = BrandCardsViewModel(tmdb, mockk(relaxed = true))
        vm.init(TmdbMediaType.MOVIE)
        assertEquals(CatalogStudio.entries.map { BrowseTarget.Studio(it) }, vm.studios.value.map { it.target })
        assertTrue(vm.studios.value.all { it.imageUrl == "https://logo" && it.isLogo })
        assertEquals(CatalogSaga.entries.size, vm.sagas.value.size)
        assertEquals(
            "https://backdrop",
            vm.sagas.value
                .first()
                .imageUrl,
        )
        assertEquals(
            "Toy Story",
            vm.sagas.value
                .first { it.target == BrowseTarget.Saga(CatalogSaga.TOY_STORY) }
                .name,
        )
    }

    @Test
    fun series_show_networks_and_no_sagas() {
        val vm = BrandCardsViewModel(tmdb, mockk(relaxed = true))
        vm.init(TmdbMediaType.TV)
        assertEquals(CatalogNetwork.entries.map { BrowseTarget.Network(it) }, vm.studios.value.map { it.target })
        assertTrue(vm.sagas.value.isEmpty())
    }

    @Test
    fun a_failed_logo_keeps_the_name() {
        coEvery { tmdb.company(CatalogStudio.PIXAR.companyId) } throws TmdbException("down")
        val vm = BrandCardsViewModel(tmdb, mockk(relaxed = true))
        vm.init(TmdbMediaType.MOVIE)
        val pixar = vm.studios.value.first()
        assertEquals("Pixar", pixar.name)
        assertNull(pixar.imageUrl)
        assertEquals("https://logo", vm.studios.value[1].imageUrl)
    }

    @Test
    fun logos_load_at_most_four_at_a_time() {
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var maxActive = 0
        coEvery { tmdb.company(any()) } coAnswers {
            active++
            maxActive = maxOf(maxActive, active)
            gate.await()
            active--
            TmdbBrand("x", "https://logo")
        }
        coEvery { tmdb.collection(any()) } coAnswers {
            active++
            maxActive = maxOf(maxActive, active)
            gate.await()
            active--
            TmdbCollection("x", "https://backdrop", emptyList())
        }
        val vm = BrandCardsViewModel(tmdb, mockk(relaxed = true))
        vm.init(TmdbMediaType.MOVIE)
        assertEquals(4, maxActive)
        gate.complete(Unit)
        assertTrue(vm.studios.value.all { it.imageUrl == "https://logo" })
        assertTrue(vm.sagas.value.all { it.imageUrl == "https://backdrop" })
    }
}
