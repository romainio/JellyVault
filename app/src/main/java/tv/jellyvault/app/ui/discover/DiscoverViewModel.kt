package tv.jellyvault.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.CatalogFilter
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.CatalogMediaType
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.CatalogStudio
import tv.jellyvault.app.catalog.HiddenRows
import tv.jellyvault.app.catalog.shelves.ShelfCatalog
import tv.jellyvault.app.catalog.shelves.ShelfScope
import tv.jellyvault.app.catalog.shelves.ShelfSource
import tv.jellyvault.app.catalog.taste.Recommender
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbListType
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbSort
import tv.jellyvault.app.catalog.toBaseItem
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.LatestNextUpService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.util.ResArgStringProvider
import tv.jellyvault.app.ui.util.ResStringProvider
import tv.jellyvault.app.ui.util.StringProvider
import tv.jellyvault.app.ui.util.StringStringProvider
import tv.jellyvault.app.util.HomeRowLoadingState
import java.time.LocalDate
import javax.inject.Inject

sealed interface DiscoverRowKind {
    data object Continue : DiscoverRowKind

    data object Trending : DiscoverRowKind

    data class TmdbList(
        val type: TmdbListType,
    ) : DiscoverRowKind

    data class Genre(
        val genre: CatalogGenre,
    ) : DiscoverRowKind

    data class Studio(
        val studio: CatalogStudio,
    ) : DiscoverRowKind

    data class Query(
        val query: TmdbDiscoverQuery,
    ) : DiscoverRowKind

    data object WeeklyTop10 : DiscoverRowKind

    /** A style of the shared catalog (see ShelfCatalog) */
    data class Shelf(
        val shelfId: String,
    ) : DiscoverRowKind

    /** Recommendations computed from what the user watched */
    data object ForYou : DiscoverRowKind

    /** The [slot]-th "because you watched X" row; empty when there is no such row */
    data class Because(
        val slot: Int,
    ) : DiscoverRowKind
}

data class DiscoverRowSpec(
    val kind: DiscoverRowKind,
    val title: StringProvider,
)

/**
 * Rows of the Films / Séries discovery page: continue watching from Jellyfin, then TMDB lists and genres
 */
@HiltViewModel
class DiscoverViewModel
    @Inject
    constructor(
        private val catalogRepository: CatalogRepository,
        private val latestNextUpService: LatestNextUpService,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        private val recommender: Recommender,
        private val shelfSource: ShelfSource,
    ) : ViewModel() {
        private val _rows = MutableStateFlow<List<HomeRowLoadingState>>(emptyList())
        val rows: StateFlow<List<HomeRowLoadingState>> = _rows

        val tmdbConfigured: Boolean get() = catalogRepository.isConfigured

        var rowSpecs: List<DiscoverRowSpec> = emptyList()
            private set

        private var mediaType: TmdbMediaType? = null

        /** The settings the rows were built with: coming back after changing them reloads the page */
        private var loadedWith: Pair<CatalogFilter, HiddenRows>? = null

        fun init(mediaType: TmdbMediaType) {
            val settings = CatalogFilter.current to HiddenRows.current
            if (this.mediaType == mediaType && loadedWith == settings) return
            this.mediaType = mediaType
            loadedWith = settings
            load(mediaType)
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launch { backdropService.submit(item) }
        }

        /**
         * Reload only the rows that failed
         */
        fun retry() {
            val mediaType = mediaType ?: return
            rowSpecs.forEachIndexed { index, spec ->
                if (_rows.value.getOrNull(index) is HomeRowLoadingState.Error) launchRow(index, spec, mediaType)
            }
        }

        private val jobs = mutableListOf<Job>()
        private val semaphore = Semaphore(MAX_CONCURRENT_ROWS)

        private val loader = LazyRowLoader()
        private var lastVisible = 0

        /** Bumped on every reload so rows of the previous list are never written into the new one */
        private var generation = 0

        private fun load(mediaType: TmdbMediaType) {
            jobs.forEach { it.cancel() }
            jobs.clear()
            loader.reset()
            lastVisible = 0
            generation++
            val loading = generation
            jobs +=
                viewModelScope.launch {
                    // Styles planned for this period, frozen and read from disk: no network wait
                    val plan =
                        if (catalogRepository.isConfigured) {
                            try {
                                shelfSource.plan(if (mediaType == TmdbMediaType.MOVIE) ShelfScope.MOVIES else ShelfScope.SERIES)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.w(e, "No shelf plan")
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }
                    if (loading != generation) return@launch
                    val specs =
                        specsFor(
                            mediaType,
                            catalogRepository.isConfigured,
                            shelfPlan = plan,
                            showContinue = !HiddenRows.current.continueWatching,
                        )
                    rowSpecs = specs
                    _rows.value = specs.map { HomeRowLoadingState.Pending(it.title) }
                    loadVisible()
                }
        }

        /**
         * The page shows rows up to [index]: load the ones coming next
         */
        fun onVisibleRow(index: Int) {
            lastVisible = maxOf(lastVisible, index)
            loadVisible()
        }

        private fun loadVisible() {
            val mediaType = mediaType ?: return
            loader.next(lastVisible, rowSpecs.size).forEach { launchRow(it, rowSpecs[it], mediaType) }
        }

        private fun launchRow(
            index: Int,
            spec: DiscoverRowSpec,
            mediaType: TmdbMediaType,
        ) {
            _rows.update { rows -> rows.toMutableList().also { it[index] = HomeRowLoadingState.Loading(spec.title) } }
            val launchedFor = generation
            jobs +=
                viewModelScope.launchIO {
                    val state = semaphore.withPermit { loadRow(spec, mediaType) }
                    if (launchedFor == generation) {
                        _rows.update { rows -> rows.toMutableList().also { it[index] = state } }
                    }
                }
        }

        private suspend fun loadRow(
            spec: DiscoverRowSpec,
            mediaType: TmdbMediaType,
        ): HomeRowLoadingState =
            try {
                var title = spec.title
                val items: List<BaseItem> =
                    when (val kind = spec.kind) {
                        DiscoverRowKind.ForYou -> {
                            recommender.forYou(mediaType).map { it.toBaseItem() }
                        }

                        is DiscoverRowKind.Because -> {
                            val row = recommender.because(mediaType).getOrNull(kind.slot)
                            if (row != null) title = ResArgStringProvider(R.string.discover_because, row.seedTitle)
                            row?.cards?.map { it.toBaseItem() }.orEmpty()
                        }

                        DiscoverRowKind.Continue -> {
                            continueItems(mediaType)
                        }

                        DiscoverRowKind.Trending -> {
                            catalogRepository.trendingRow(mediaType).map { it.toBaseItem() }
                        }

                        is DiscoverRowKind.TmdbList -> {
                            catalogRepository.listRow(kind.type, mediaType).map { it.toBaseItem() }
                        }

                        is DiscoverRowKind.Studio -> {
                            catalogRepository
                                .queryRow(TmdbDiscoverQuery(TmdbMediaType.MOVIE, companyId = kind.studio.companyId, minRating = 5.0))
                                .map { it.toBaseItem() }
                        }

                        is DiscoverRowKind.Query -> {
                            catalogRepository.queryRow(kind.query).map { it.toBaseItem() }
                        }

                        DiscoverRowKind.WeeklyTop10 -> {
                            catalogRepository.weeklyTop10Row().map { it.toBaseItem() }
                        }

                        is DiscoverRowKind.Shelf -> {
                            ShelfCatalog
                                .byId(
                                    kind.shelfId,
                                )?.let { catalogRepository.shelfRow(it, LocalDate.now()) }
                                .orEmpty()
                                .map { it.toBaseItem() }
                        }

                        is DiscoverRowKind.Genre -> {
                            catalogRepository
                                .genreRow(kind.genre, if (mediaType == TmdbMediaType.MOVIE) CatalogMediaType.MOVIE else CatalogMediaType.TV)
                                .map { it.toBaseItem() }
                        }
                    }
                HomeRowLoadingState.Success(title = title, items = items, showViewMore = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Discover row failed: %s", spec.kind)
                HomeRowLoadingState.Error(spec.title, exception = e)
            }

        private suspend fun continueItems(mediaType: TmdbMediaType): List<BaseItem> {
            val userId = serverRepository.currentUser?.id ?: return emptyList()
            val kind = if (mediaType == TmdbMediaType.MOVIE) BaseItemKind.MOVIE else BaseItemKind.EPISODE
            // Movies and episodes share the resume list: ask for more, then keep this page's type
            return latestNextUpService
                .getResume(userId, CONTINUE_LIMIT * 2, includeEpisodes = mediaType == TmdbMediaType.TV)
                .filter { it.type == kind }
                .take(CONTINUE_LIMIT)
        }

        companion object {
            const val MAX_CONCURRENT_ROWS = 4
            const val CONTINUE_LIMIT = 20

            /**
             * Series: the weekly top 10 and trending per streaming platform, then trending and top rated per platform
             */
            private fun platformSpecs(today: LocalDate): List<DiscoverRowSpec> {
                val since = today.minusYears(1)

                fun trending(
                    ids: List<Int>,
                    name: String,
                ) = DiscoverRowSpec(
                    DiscoverRowKind.Query(TmdbDiscoverQuery(TmdbMediaType.TV, networkIds = ids, airedAfter = since, minVotes = 50)),
                    ResArgStringProvider(R.string.discover_platform_trending, name),
                )

                fun top(
                    ids: List<Int>,
                    name: String,
                ) = DiscoverRowSpec(
                    DiscoverRowKind.Query(TmdbDiscoverQuery(TmdbMediaType.TV, networkIds = ids, sort = TmdbSort.RATING, minVotes = 300)),
                    ResArgStringProvider(R.string.discover_platform_top_rated, name),
                )
                // The weekly top 10 opens the "Tendances séries" row
                return listOf(
                    trending(listOf(NETFLIX), "Netflix"),
                    trending(HBO_MAX, "HBO Max"),
                    top(HBO_MAX, "HBO Max"),
                    trending(listOf(CANAL_PLUS), "Canal+"),
                    top(listOf(CANAL_PLUS), "Canal+"),
                    trending(listOf(PRIME_VIDEO), "Prime Video"),
                    top(listOf(PRIME_VIDEO), "Prime Video"),
                )
            }

            private const val NETFLIX = 213
            private val HBO_MAX = listOf(49, 3186)
            private const val CANAL_PLUS = 285
            private const val PRIME_VIDEO = 1024

            fun specsFor(
                mediaType: TmdbMediaType,
                tmdbConfigured: Boolean,
                today: LocalDate = LocalDate.now(),
                shelfPlan: List<String> = emptyList(),
                showContinue: Boolean = true,
            ): List<DiscoverRowSpec> {
                val continueRow =
                    DiscoverRowSpec(DiscoverRowKind.Continue, ResStringProvider(R.string.continue_watching)).takeIf { showContinue }
                if (!tmdbConfigured) return listOfNotNull(continueRow)
                val movie = mediaType == TmdbMediaType.MOVIE
                return buildList {
                    continueRow?.let(::add)
                    add(
                        DiscoverRowSpec(
                            DiscoverRowKind.Trending,
                            ResStringProvider(if (movie) R.string.catalog_trending_movies else R.string.catalog_trending_series),
                        ),
                    )
                    add(DiscoverRowSpec(DiscoverRowKind.ForYou, ResStringProvider(R.string.discover_for_you_row)))
                    repeat(
                        Recommender.BECAUSE_ROWS,
                    ) { add(DiscoverRowSpec(DiscoverRowKind.Because(it), ResStringProvider(R.string.discover_because_soon))) }
                    if (!movie) addAll(platformSpecs(today))
                    add(DiscoverRowSpec(DiscoverRowKind.TmdbList(TmdbListType.POPULAR), ResStringProvider(R.string.discover_popular)))
                    add(DiscoverRowSpec(DiscoverRowKind.TmdbList(TmdbListType.TOP_RATED), ResStringProvider(R.string.discover_top_rated)))
                    if (movie) {
                        CatalogStudio.FOR_YOU.forEach {
                            add(DiscoverRowSpec(DiscoverRowKind.Studio(it), StringStringProvider(it.displayName)))
                        }
                    } else {
                        add(
                            DiscoverRowSpec(
                                DiscoverRowKind.TmdbList(TmdbListType.ON_THE_AIR),
                                ResStringProvider(R.string.discover_on_the_air),
                            ),
                        )
                    }
                    // The styles of the period replace the fixed genre rows (genres stay in their own tab)
                    shelfPlan.mapNotNull(ShelfCatalog::byId).forEach {
                        add(DiscoverRowSpec(DiscoverRowKind.Shelf(it.id), ResStringProvider(it.titleRes)))
                    }
                }
            }
        }
    }
