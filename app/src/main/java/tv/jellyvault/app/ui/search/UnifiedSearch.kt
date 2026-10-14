package tv.jellyvault.app.ui.search

import org.jellyfin.sdk.model.api.BaseItemKind
import tv.jellyvault.app.data.model.BaseItem

private val UNIFIED_KINDS = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)

/**
 * One grid of movies and series: TMDB results first (owned ones are the Jellyfin items, with ✓),
 * then Jellyfin movies and series TMDB did not return. People, episodes, etc. are left out.
 */
fun mergeSearchResults(
    jellyfin: SearchResult,
    tmdb: SearchResult,
): SearchResult {
    if (jellyfin == SearchResult.Searching || tmdb == SearchResult.Searching) return SearchResult.Searching
    if (jellyfin == SearchResult.NoQuery && tmdb == SearchResult.NoQuery) return SearchResult.NoQuery
    val tmdbItems = (tmdb as? SearchResult.Success)?.items.orEmpty().filterNotNull()
    val jellyfinItems =
        (jellyfin as? SearchResult.Success)
            ?.items
            .orEmpty()
            .filterNotNull()
            .filter { it.type in UNIFIED_KINDS }
    if (jellyfin is SearchResult.Error && tmdb !is SearchResult.Success) return jellyfin
    val shown = tmdbItems.mapTo(HashSet()) { it.id }
    return SearchResult.Success(tmdbItems + jellyfinItems.filter { shown.add(it.id) })
}

/**
 * Same query as the one on screen: keep the results (and focus) instead of searching again
 */
fun isRepeatedSearch(
    current: String?,
    query: String?,
    force: Boolean,
): Boolean = !force && current != null && current == query
