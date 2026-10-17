package tv.jellyvault.app.catalog

import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.preferences.CatalogPopularity
import tv.jellyvault.app.preferences.CatalogPreferences
import java.time.LocalDate
import kotlin.math.roundToInt

/** Below this many votes TMDB averages mean nothing, whatever the popularity setting */
private const val ABSOLUTE_MIN_VOTES = 30

/**
 * The user's limits on TMDB suggestions (Settings › Advanced › Catalog): no title older than [minYear], and a vote floor
 * scaled by [voteFactor]. Search results and sagas are never filtered: the user asked for those titles.
 */
data class CatalogFilter(
    /** 0 for no limit */
    val minYear: Int = 0,
    val voteFactor: Double = 1.0,
) {
    private val firstDay: LocalDate? get() = minYear.takeIf { it > 0 }?.let { LocalDate.of(it, 1, 1) }

    /**
     * [query] within the limits, null when nothing can match (a "before 2000" row with titles from 2000 on)
     */
    fun apply(query: TmdbDiscoverQuery): TmdbDiscoverQuery? {
        val first = firstDay
        if (first != null && query.releasedBefore != null && query.releasedBefore < first) return null
        if (first == null && voteFactor == 1.0) return query
        return query.copy(
            releasedAfter = listOfNotNull(query.releasedAfter, first).maxOrNull(),
            minVotes = (query.minVotes * voteFactor).roundToInt().coerceAtLeast(minOf(query.minVotes, ABSOLUTE_MIN_VOTES)),
            // A "lesser known" ceiling moves with the floor, or the row would empty
            maxVotes = query.maxVotes?.let { (it * voteFactor).roundToInt() },
        )
    }

    /** Whether a title of a ready-made TMDB list (trending, recommendations) is recent enough */
    fun keeps(item: TmdbItem): Boolean = minYear <= 0 || (item.year ?: Int.MAX_VALUE) >= minYear

    companion object {
        /** Set from the preferences as soon as they load, read at each TMDB request */
        @Volatile
        var current: CatalogFilter = CatalogFilter()

        fun of(prefs: CatalogPreferences): CatalogFilter =
            CatalogFilter(
                minYear = prefs.minYear,
                voteFactor =
                    when (prefs.popularity) {
                        CatalogPopularity.POPULARITY_DISCOVERY -> 0.5
                        CatalogPopularity.POPULARITY_HITS -> 3.0
                        else -> 1.0
                    },
            )
    }
}

fun List<TmdbItem>.withinCatalogFilter(filter: CatalogFilter = CatalogFilter.current): List<TmdbItem> = filter { filter.keeps(it) }
