package tv.jellyvault.app.ui.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.toBaseItem

/**
 * TMDB part of the search: movies and series matching the query, including titles not on the server.
 * Only the latest query updates [results].
 */
class TmdbSearch(
    private val catalogRepository: CatalogRepository,
    private val scope: CoroutineScope,
) {
    private val _results = MutableStateFlow<SearchResult>(SearchResult.NoQuery)
    val results: StateFlow<SearchResult> = _results

    private var job: Job? = null
    private var generation = 0

    fun search(query: String?) {
        job?.cancel()
        val current = ++generation
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty() || !catalogRepository.isConfigured) {
            _results.value = SearchResult.NoQuery
            return
        }
        _results.value = SearchResult.Searching
        job =
            scope.launch {
                val result =
                    try {
                        SearchResult.Success(catalogRepository.searchCards(trimmed).map { it.toBaseItem() })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "TMDB search failed")
                        SearchResult.Error(e)
                    }
                if (current == generation) _results.value = result
            }
    }
}
