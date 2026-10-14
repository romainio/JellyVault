package tv.jellyvault.app.catalog

import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.shelves.ShelfCatalog
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.ui.util.ResProviderStringProvider
import tv.jellyvault.app.ui.util.ResStringProvider
import tv.jellyvault.app.ui.util.StringProvider

/**
 * The catalog rows of the default JellyVault home page, empty if TMDB is not configured
 */
fun defaultCatalogRows(isConfigured: Boolean): List<HomeRowConfig> =
    if (!isConfigured) {
        emptyList()
    } else {
        listOf(
            HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE),
            HomeRowConfig.TmdbTrending(TmdbMediaType.TV),
        ) + personalRows() + HomeRowConfig.TmdbWatchlist() + HomeRowConfig.TmdbShelves()
    }

/**
 * The rows computed from what the user watched: they only show once there is a history
 */
fun personalRows(): List<HomeRowConfig> =
    listOf(
        HomeRowConfig.TmdbForYou(TmdbMediaType.MOVIE),
        HomeRowConfig.TmdbBecause(TmdbMediaType.MOVIE, 0),
        HomeRowConfig.TmdbForYou(TmdbMediaType.TV),
    )

/**
 * Title for a catalog row, or null if [config] is not a catalog row
 */
fun catalogRowTitle(config: HomeRowConfig): StringProvider? =
    when (config) {
        is HomeRowConfig.TmdbTrending -> {
            ResStringProvider(
                if (config.mediaType == TmdbMediaType.MOVIE) R.string.catalog_trending_movies else R.string.catalog_trending_series,
            )
        }

        is HomeRowConfig.TmdbForYou -> {
            ResStringProvider(
                if (config.mediaType == TmdbMediaType.MOVIE) R.string.catalog_for_you_movies else R.string.catalog_for_you_series,
            )
        }

        is HomeRowConfig.TmdbBecause -> {
            ResStringProvider(R.string.discover_because_soon)
        }

        is HomeRowConfig.TmdbWatchlist -> {
            ResStringProvider(R.string.home_watchlist)
        }

        is HomeRowConfig.TmdbShelves -> {
            ResStringProvider(R.string.home_varied_styles)
        }

        is HomeRowConfig.TmdbShelf -> {
            ShelfCatalog.byId(config.shelfId)?.let { ResStringProvider(it.titleRes) }
        }

        is HomeRowConfig.TmdbGenre -> {
            val genre = ResStringProvider(config.genre.titleRes)
            when (config.mediaType) {
                CatalogMediaType.BOTH -> genre
                CatalogMediaType.MOVIE -> ResProviderStringProvider(R.string.catalog_genre_movies_format, genre)
                CatalogMediaType.TV -> ResProviderStringProvider(R.string.catalog_genre_series_format, genre)
            }
        }

        else -> {
            null
        }
    }

/**
 * Default JellyVault home order: trending movies and movies for you, then continue watching, then the other
 * catalog rows (trending series, personal rows, varied styles), then libraries
 */
fun <T> defaultHomeOrder(
    continueRows: List<T>,
    catalogRows: List<T>,
    libraryRows: List<T>,
    config: (T) -> HomeRowConfig,
): List<T> {
    val (top, others) = catalogRows.partition { config(it).isAboveContinue() }
    return top + continueRows + others + libraryRows
}

private fun HomeRowConfig.isAboveContinue() =
    this == HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE, viewOptions) ||
        this == HomeRowConfig.TmdbForYou(TmdbMediaType.MOVIE, viewOptions)

fun defaultHomeOrder(
    continueRows: List<HomeRowConfig>,
    catalogRows: List<HomeRowConfig>,
    libraryRows: List<HomeRowConfig>,
): List<HomeRowConfig> = defaultHomeOrder(continueRows, catalogRows, libraryRows) { it }

/**
 * Home pages saved with the first JellyVault default (continue watching, then trending movies and series)
 * get the trending rows moved to the top. Any other order was chosen by the user and is kept.
 */
fun migrateHomeOrder(rows: List<HomeRowConfig>): List<HomeRowConfig> {
    if (rows.size < 3) return rows
    val (first, second, third) = rows
    val oldDefault =
        first is HomeRowConfig.ContinueWatchingCombined &&
            second == HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE, second.viewOptions) &&
            third == HomeRowConfig.TmdbTrending(TmdbMediaType.TV, third.viewOptions)
    return if (oldDefault) listOf(second, third, first) + rows.drop(3) else rows
}

/**
 * Saved home pages that show TMDB trending rows get the personal rows right after the last one
 */
fun addPersonalRows(rows: List<HomeRowConfig>): List<HomeRowConfig> {
    if (rows.any { it is HomeRowConfig.TmdbForYou || it is HomeRowConfig.TmdbBecause }) return rows
    val lastTrending = rows.indexOfLast { it is HomeRowConfig.TmdbTrending }
    if (lastTrending < 0) return rows
    return rows.take(lastTrending + 1) + personalRows() + rows.drop(lastTrending + 1)
}
