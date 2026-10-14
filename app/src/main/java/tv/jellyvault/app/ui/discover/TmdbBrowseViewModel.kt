package tv.jellyvault.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import tv.jellyvault.app.catalog.BrowseRowKind
import tv.jellyvault.app.catalog.BrowseRowSpec
import tv.jellyvault.app.catalog.BrowseTarget
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.browseRows
import tv.jellyvault.app.catalog.gridQuery
import tv.jellyvault.app.catalog.toBaseItem
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.util.HomeRowLoadingState
import java.time.LocalDate
import javax.inject.Inject

/**
 * A genre, studio, network or saga page: themed rows loaded as the page scrolls, then the full grid
 */
@HiltViewModel
class TmdbBrowseViewModel
    @Inject
    constructor(
        private val catalogRepository: CatalogRepository,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        private val _rows = MutableStateFlow<List<HomeRowLoadingState>>(emptyList())
        val rows: StateFlow<List<HomeRowLoadingState>> = _rows

        private val _grid = MutableStateFlow<TmdbGridPager?>(null)

        /** The full grid under the rows, null for a saga */
        val grid: StateFlow<TmdbGridPager?> = _grid

        internal var today: () -> LocalDate = LocalDate::now

        private var target: BrowseTarget? = null
        private var specs: List<BrowseRowSpec> = emptyList()
        private val loader = LazyRowLoader()
        private val semaphore = Semaphore(DiscoverViewModel.MAX_CONCURRENT_ROWS)
        private var lastVisible = 0

        fun init(target: BrowseTarget) {
            if (this.target == target) return
            this.target = target
            specs = browseRows(target, today())
            _rows.value = specs.map { HomeRowLoadingState.Pending(it.title) }
            _grid.value =
                gridQuery(target)?.let { query ->
                    TmdbGridPager(viewModelScope) { page -> catalogRepository.queryPage(query, page) }.also { it.start() }
                }
            loadVisible()
        }

        /**
         * The page shows rows up to [index]: load the ones coming next
         */
        fun onVisibleRow(index: Int) {
            lastVisible = maxOf(lastVisible, index)
            loadVisible()
        }

        /**
         * Reload the rows that failed, and the grid page that failed
         */
        fun retry() {
            specs.indices.filter { _rows.value.getOrNull(it) is HomeRowLoadingState.Error }.forEach(::launchRow)
            _grid.value?.let { if (it.error.value != null) it.retry() }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launch { backdropService.submit(item) }
        }

        private fun loadVisible() = loader.next(lastVisible, specs.size).forEach(::launchRow)

        private fun launchRow(index: Int) {
            val spec = specs[index]
            _rows.update { rows -> rows.toMutableList().also { it[index] = HomeRowLoadingState.Loading(spec.title) } }
            viewModelScope.launchIO {
                val state =
                    semaphore.withPermit {
                        try {
                            val cards =
                                when (val kind = spec.kind) {
                                    is BrowseRowKind.Query -> catalogRepository.queryRow(kind.query)
                                    is BrowseRowKind.SagaParts -> catalogRepository.sagaRow(kind.saga)
                                    is BrowseRowKind.CollectionParts -> catalogRepository.collectionRow(kind.collectionId)
                                }
                            HomeRowLoadingState.Success(spec.title, cards.map { it.toBaseItem() }, showViewMore = false)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "Browse row failed: %s", spec.kind)
                            HomeRowLoadingState.Error(spec.title, exception = e)
                        }
                    }
                _rows.update { rows -> rows.toMutableList().also { it[index] = state } }
            }
        }
    }
