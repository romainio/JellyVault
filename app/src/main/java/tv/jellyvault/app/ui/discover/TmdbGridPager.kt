package tv.jellyvault.app.ui.discover

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import tv.jellyvault.app.catalog.MediaCard
import tv.jellyvault.app.catalog.toBaseItem
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.ui.launchIO

/**
 * A TMDB grid loaded one page (20 titles) at a time as it scrolls
 */
class TmdbGridPager(
    private val scope: CoroutineScope,
    private val loadPage: suspend (page: Int) -> Pair<List<MediaCard>, Int>,
) {
    private val _items = MutableStateFlow<List<BaseItem>>(emptyList())
    val items: StateFlow<List<BaseItem>> = _items

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _empty = MutableStateFlow(false)

    /** The grid has no titles at all */
    val empty: StateFlow<Boolean> = _empty

    private var started = false
    private var loadedPages = 0
    private var totalPages = Int.MAX_VALUE

    /** TMDB never serves more than 500 pages; overridable in tests */
    internal var maxPages = MAX_PAGES

    fun start() {
        if (started) return
        started = true
        loadNextPage()
    }

    /**
     * The grid shows [index]: load the next page when close to the end of what is loaded
     */
    fun onPosition(index: Int) {
        if (index >= _items.value.size - PREFETCH_DISTANCE) loadNextPage()
    }

    /**
     * Try the page that failed again
     */
    fun retry() {
        _error.value = null
        loadNextPage()
    }

    private fun loadNextPage() {
        if (!started || _loading.value || loadedPages >= minOf(totalPages, maxPages)) return
        _loading.value = true
        val page = loadedPages + 1
        scope.launchIO {
            try {
                val (cards, total) = loadPage(page)
                totalPages = total
                loadedPages = page
                _items.update { current ->
                    val known = current.mapTo(HashSet()) { it.gridId }
                    // TMDB pages sorted by popularity can shift between requests: drop titles already shown
                    current + cards.map { card -> card.toBaseItem() }.filter { known.add(it.gridId) }
                }
                _empty.value = page == 1 && _items.value.isEmpty()
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Grid page %s failed", page)
                _error.value = e
            } finally {
                _loading.value = false
            }
        }
    }

    companion object {
        const val PREFETCH_DISTANCE = 12
        const val MAX_PAGES = 500
    }
}
