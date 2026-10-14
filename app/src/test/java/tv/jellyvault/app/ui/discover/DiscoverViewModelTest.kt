package tv.jellyvault.app.ui.discover

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.CatalogMediaType
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.CatalogStudio
import tv.jellyvault.app.catalog.MediaCard
import tv.jellyvault.app.catalog.shelves.ShelfScope
import tv.jellyvault.app.catalog.shelves.ShelfSource
import tv.jellyvault.app.catalog.taste.BecauseCards
import tv.jellyvault.app.catalog.taste.Recommender
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbListType
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.JellyfinUser
import tv.jellyvault.app.services.LatestNextUpService
import tv.jellyvault.app.ui.util.ResArgStringProvider
import tv.jellyvault.app.util.HomeRowLoadingState
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val catalog = mockk<CatalogRepository>()
    private val nextUp = mockk<LatestNextUpService>()
    private val servers = mockk<ServerRepository>()
    private val userId = UUID.randomUUID()
    private val shelves = mockk<ShelfSource>()

    /** "Recommended for you" plus its "because you watched" rows, right after trending */
    private val personal = 1 + Recommender.BECAUSE_ROWS

    private fun card(id: Int) = MediaCard.Remote(TmdbItem(id, TmdbMediaType.MOVIE, "T$id", null, "/p.jpg", null, null, null, emptyList()))

    private fun base(kind: BaseItemKind) = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = kind))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        JvDispatchers.configure(dispatcher)
        every { servers.currentUser } returns JellyfinUser(id = userId, name = "u", serverId = UUID.randomUUID(), accessToken = "t")
        every { catalog.isConfigured } returns true
        coEvery { catalog.trendingRow(any()) } returns listOf(card(1))
        coEvery { catalog.listRow(any(), any()) } returns listOf(card(2))
        coEvery { catalog.genreRow(any(), any()) } returns listOf(card(3))
        coEvery { catalog.queryRow(any()) } returns listOf(card(4))
        coEvery { catalog.weeklyTop10Row() } returns listOf(card(5))
        coEvery { catalog.shelfRow(any(), any()) } returns listOf(card(6))
        coEvery { shelves.plan(ShelfScope.MOVIES, any()) } returns listOf("fr_films", "comedies")
        coEvery { shelves.plan(ShelfScope.SERIES, any()) } returns listOf("tv_crime")
        coEvery { nextUp.getResume(userId, any(), any(), any()) } returns listOf(base(BaseItemKind.MOVIE), base(BaseItemKind.EPISODE))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        JvDispatchers.reset()
    }

    private fun titles(vm: DiscoverViewModel) = vm.rowSpecs.map { it.kind }

    @Test
    fun rows_are_in_order_for_movies() =
        runTest(dispatcher) {
            val vm =
                DiscoverViewModel(
                    catalog,
                    nextUp,
                    servers,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    shelves,
                ).also {
                    it.init(TmdbMediaType.MOVIE)
                }
            val kinds = titles(vm)
            assertEquals(
                listOf(
                    DiscoverRowKind.Continue,
                    DiscoverRowKind.Trending,
                    DiscoverRowKind.ForYou,
                    DiscoverRowKind.Because(0),
                    DiscoverRowKind.Because(1),
                    DiscoverRowKind.TmdbList(TmdbListType.POPULAR),
                    DiscoverRowKind.TmdbList(TmdbListType.TOP_RATED),
                ),
                kinds.take(4 + personal),
            )
            // Nothing in cinemas: Radarr cannot download those yet
            assertTrue(kinds.none { it == DiscoverRowKind.TmdbList(TmdbListType.NOW_PLAYING) })
            assertEquals(
                listOf(DiscoverRowKind.Shelf("fr_films"), DiscoverRowKind.Shelf("comedies")),
                kinds.drop(4 + personal + CatalogStudio.FOR_YOU.size),
            )
            assertEquals(kinds.size, vm.rows.value.size)
            vm.onVisibleRow(kinds.lastIndex)
            assertTrue(vm.rows.value.all { it is HomeRowLoadingState.Success })
        }

    @Test
    fun series_use_on_the_air_then_the_series_styles() =
        runTest(dispatcher) {
            val vm =
                DiscoverViewModel(
                    catalog,
                    nextUp,
                    servers,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    shelves,
                ).also {
                    it.init(TmdbMediaType.TV)
                }
            val kinds = titles(vm)
            // Continue, Trending (with the weekly top 10), 7 platform rows, Popular, Top rated, then On the air
            assertEquals(DiscoverRowKind.TmdbList(TmdbListType.ON_THE_AIR), kinds[11 + personal])
            assertEquals(listOf(DiscoverRowKind.Shelf("tv_crime")), kinds.drop(12 + personal))
            assertTrue(kinds.none { it == DiscoverRowKind.WeeklyTop10 })
        }

    @Test
    fun continue_row_keeps_only_movies() =
        runTest(dispatcher) {
            val vm =
                DiscoverViewModel(
                    catalog,
                    nextUp,
                    servers,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    shelves,
                ).also {
                    it.init(TmdbMediaType.MOVIE)
                }
            val row = vm.rows.value[0] as HomeRowLoadingState.Success
            assertEquals(listOf(BaseItemKind.MOVIE), row.items.map { it!!.type })
        }

    @Test
    fun a_failed_row_does_not_affect_others() =
        runTest(dispatcher) {
            coEvery { catalog.listRow(TmdbListType.TOP_RATED, TmdbMediaType.MOVIE) } throws TmdbException("down")
            val vm =
                DiscoverViewModel(
                    catalog,
                    nextUp,
                    servers,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    shelves,
                ).also {
                    it.init(TmdbMediaType.MOVIE)
                }
            vm.onVisibleRow(7)
            val rows = vm.rows.value
            assertTrue(rows[3 + personal] is HomeRowLoadingState.Error)
            assertTrue(rows[2] is HomeRowLoadingState.Success)
            assertTrue(rows[2 + personal] is HomeRowLoadingState.Success)
        }

    @Test
    fun no_tmdb_only_continue_row() =
        runTest(dispatcher) {
            every { catalog.isConfigured } returns false
            val vm =
                DiscoverViewModel(
                    catalog,
                    nextUp,
                    servers,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    shelves,
                ).also {
                    it.init(TmdbMediaType.MOVIE)
                }
            assertEquals(listOf(DiscoverRowKind.Continue), titles(vm))
        }

    @Test
    fun retry_reloads_only_failed_rows() =
        runTest(dispatcher) {
            coEvery { catalog.listRow(TmdbListType.TOP_RATED, TmdbMediaType.MOVIE) } throws TmdbException("down")
            val vm =
                DiscoverViewModel(
                    catalog,
                    nextUp,
                    servers,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    shelves,
                ).also {
                    it.init(TmdbMediaType.MOVIE)
                }
            vm.onVisibleRow(7)
            coEvery { catalog.listRow(TmdbListType.TOP_RATED, TmdbMediaType.MOVIE) } returns listOf(card(9))
            vm.retry()
            assertTrue(vm.rows.value[3 + personal] is HomeRowLoadingState.Success)
            coVerify(exactly = 1) { catalog.listRow(TmdbListType.POPULAR, TmdbMediaType.MOVIE) }
            coVerify(exactly = 2) { catalog.listRow(TmdbListType.TOP_RATED, TmdbMediaType.MOVIE) }
        }

    @Test
    fun movie_for_you_has_studio_rows_after_top_rated() {
        val kinds = DiscoverViewModel.specsFor(TmdbMediaType.MOVIE, tmdbConfigured = true).map { it.kind }
        val topRated = kinds.indexOf(DiscoverRowKind.TmdbList(TmdbListType.TOP_RATED))
        assertEquals(
            CatalogStudio.FOR_YOU.map { DiscoverRowKind.Studio(it) },
            kinds.subList(topRated + 1, topRated + 1 + CatalogStudio.FOR_YOU.size),
        )
        assertTrue(DiscoverViewModel.specsFor(TmdbMediaType.TV, true).none { it.kind is DiscoverRowKind.Studio })
    }

    @Test
    fun only_the_first_rows_load_until_the_user_scrolls() =
        runTest(dispatcher) {
            val vm =
                DiscoverViewModel(catalog, nextUp, servers, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), shelves)
            vm.init(TmdbMediaType.MOVIE)
            assertEquals(5, vm.rows.value.count { it is HomeRowLoadingState.Success })
            assertTrue(
                vm.rows.value
                    .drop(5)
                    .all { it is HomeRowLoadingState.Pending },
            )
            vm.onVisibleRow(6)
            assertEquals(10, vm.rows.value.count { it is HomeRowLoadingState.Success })
        }

    @Test
    fun switching_type_starts_again_from_the_first_rows() =
        runTest(dispatcher) {
            val vm =
                DiscoverViewModel(catalog, nextUp, servers, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), shelves)
            vm.init(TmdbMediaType.MOVIE)
            vm.onVisibleRow(20)
            vm.init(TmdbMediaType.TV)
            assertEquals(5, vm.rows.value.count { it is HomeRowLoadingState.Success })
        }

    @Test
    fun studio_rows_query_the_studio_with_the_rating_floor() =
        runTest(dispatcher) {
            val vm =
                DiscoverViewModel(catalog, nextUp, servers, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), shelves)
            vm.init(TmdbMediaType.MOVIE)
            vm.onVisibleRow(8)
            coVerify { catalog.queryRow(TmdbDiscoverQuery(TmdbMediaType.MOVIE, companyId = 3, minRating = 5.0)) }
        }

    @Test
    fun series_for_you_has_the_platform_rows_in_order() {
        val kinds = DiscoverViewModel.specsFor(TmdbMediaType.TV, true, java.time.LocalDate.of(2026, 9, 28)).map { it.kind }
        assertEquals(DiscoverRowKind.Trending, kinds[1])
        val platform = kinds.subList(2 + personal, 9 + personal).map { (it as DiscoverRowKind.Query).query }
        assertEquals(listOf(213), platform[0].networkIds)
        assertEquals(listOf(49, 3186), platform[1].networkIds)
        assertEquals(java.time.LocalDate.of(2025, 9, 28), platform[1].airedAfter)
        assertEquals(tv.jellyvault.app.catalog.tmdb.TmdbSort.RATING, platform[2].sort)
        assertEquals(listOf(285), platform[3].networkIds)
        assertEquals(listOf(1024), platform[5].networkIds)
        assertEquals(DiscoverRowKind.TmdbList(TmdbListType.POPULAR), kinds[9 + personal])
    }

    @Test
    fun a_style_row_loads_its_shelf() =
        runTest(dispatcher) {
            val vm =
                DiscoverViewModel(catalog, nextUp, servers, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), shelves)
            vm.init(TmdbMediaType.TV)
            vm.onVisibleRow(titles(vm).lastIndex)
            val last = vm.rows.value.last() as HomeRowLoadingState.Success
            assertEquals(1, last.items.size)
            coVerify {
                catalog.shelfRow(
                    tv.jellyvault.app.catalog.shelves.ShelfCatalog
                        .byId("tv_crime")!!,
                    any(),
                )
            }
        }

    @Test
    fun personal_rows_show_the_recommender_and_name_the_seed() =
        runTest(dispatcher) {
            val recommender = mockk<Recommender>()
            coEvery { recommender.forYou(TmdbMediaType.MOVIE) } returns listOf(card(7))
            coEvery { recommender.because(TmdbMediaType.MOVIE) } returns listOf(BecauseCards("Heat", listOf(card(8))))
            val vm = DiscoverViewModel(catalog, nextUp, servers, mockk(relaxed = true), mockk(relaxed = true), recommender, shelves)
            vm.init(TmdbMediaType.MOVIE)
            val forYou = vm.rows.value[2] as HomeRowLoadingState.Success
            val because = vm.rows.value[3] as HomeRowLoadingState.Success
            val noSecondRow = vm.rows.value[4] as HomeRowLoadingState.Success
            assertEquals(1, forYou.items.size)
            assertEquals(ResArgStringProvider(R.string.discover_because, "Heat"), because.title)
            assertEquals(1, because.items.size)
            assertTrue(noSecondRow.items.isEmpty())
        }

    @Test
    fun a_failing_recommender_only_fails_its_row() =
        runTest(dispatcher) {
            val recommender = mockk<Recommender>()
            coEvery { recommender.forYou(any()) } throws TmdbException("down")
            coEvery { recommender.because(any()) } returns emptyList()
            val vm = DiscoverViewModel(catalog, nextUp, servers, mockk(relaxed = true), mockk(relaxed = true), recommender, shelves)
            vm.init(TmdbMediaType.MOVIE)
            assertTrue(vm.rows.value[2] is HomeRowLoadingState.Error)
            assertTrue(vm.rows.value[1] is HomeRowLoadingState.Success)
        }
}
