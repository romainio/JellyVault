package tv.jellyvault.app.catalog

import kotlinx.serialization.Serializable
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbSort
import tv.jellyvault.app.ui.util.ResStringProvider
import tv.jellyvault.app.ui.util.StringProvider
import tv.jellyvault.app.ui.util.StringStringProvider
import java.time.LocalDate

/**
 * What a browse page shows: a genre, a studio, a network or a saga
 */
@Serializable
sealed interface BrowseTarget {
    val mediaType: TmdbMediaType

    @Serializable
    data class Genre(
        val genre: CatalogGenre,
        override val mediaType: TmdbMediaType,
    ) : BrowseTarget

    @Serializable
    data class Studio(
        val studio: CatalogStudio,
    ) : BrowseTarget {
        override val mediaType: TmdbMediaType get() = TmdbMediaType.MOVIE
    }

    @Serializable
    data class Network(
        val network: CatalogNetwork,
    ) : BrowseTarget {
        override val mediaType: TmdbMediaType get() = TmdbMediaType.TV
    }

    @Serializable
    data class Saga(
        val saga: CatalogSaga,
    ) : BrowseTarget {
        override val mediaType: TmdbMediaType get() = TmdbMediaType.MOVIE
    }

    /** Any TMDB studio, e.g. opened from a detail page */
    @Serializable
    data class AnyCompany(
        val id: Int,
        val name: String,
    ) : BrowseTarget {
        override val mediaType: TmdbMediaType get() = TmdbMediaType.MOVIE
    }

    @Serializable
    data class AnyNetwork(
        val id: Int,
        val name: String,
    ) : BrowseTarget {
        override val mediaType: TmdbMediaType get() = TmdbMediaType.TV
    }

    @Serializable
    data class AnyCollection(
        val id: Int,
        val name: String,
    ) : BrowseTarget {
        override val mediaType: TmdbMediaType get() = TmdbMediaType.MOVIE
    }
}

sealed interface BrowseRowKind {
    data class CollectionParts(
        val collectionId: Int,
    ) : BrowseRowKind

    data class Query(
        val query: TmdbDiscoverQuery,
    ) : BrowseRowKind

    data class SagaParts(
        val saga: CatalogSaga,
    ) : BrowseRowKind
}

data class BrowseRowSpec(
    val kind: BrowseRowKind,
    val title: StringProvider,
)

private const val POPULAR_MIN_VOTES = 200
private const val POPULAR_MIN_RATING = 5.0
private const val TOP_MIN_VOTES = 1000
private const val TOP_MIN_VOTES_BRAND = 300

/** A studio or network rarely gets 200 votes on a title of the last 12 months */
private const val NEW_MIN_VOTES_BRAND = 50
private const val GEMS_MIN_RATING = 7.5
private const val GEMS_MAX_VOTES = 2000
private const val CLASSICS_MIN_VOTES_MOVIE = 500
private const val CLASSICS_MIN_VOTES_TV = 200
private val CLASSICS_BEFORE: LocalDate = LocalDate.of(1999, 12, 31)

/**
 * The query every row of [target] starts from, null if the target has nothing for its type (or is a saga)
 */
private fun baseQuery(target: BrowseTarget): TmdbDiscoverQuery? =
    when (target) {
        is BrowseTarget.Genre -> {
            val id = if (target.mediaType == TmdbMediaType.MOVIE) target.genre.movieGenreId else target.genre.tvGenreId
            id?.let { TmdbDiscoverQuery(target.mediaType, genreId = it, companyIds = target.genre.companyIds) }
        }

        is BrowseTarget.Studio -> {
            TmdbDiscoverQuery(TmdbMediaType.MOVIE, companyId = target.studio.companyId)
        }

        is BrowseTarget.Network -> {
            TmdbDiscoverQuery(TmdbMediaType.TV, networkId = target.network.networkId)
        }

        is BrowseTarget.AnyCompany -> {
            TmdbDiscoverQuery(TmdbMediaType.MOVIE, companyId = target.id)
        }

        is BrowseTarget.AnyNetwork -> {
            TmdbDiscoverQuery(TmdbMediaType.TV, networkId = target.id)
        }

        is BrowseTarget.Saga,
        is BrowseTarget.AnyCollection,
        -> {
            null
        }
    }

private fun TmdbDiscoverQuery.popular(mainstream: Boolean = false) =
    copy(minVotes = POPULAR_MIN_VOTES, minRating = POPULAR_MIN_RATING, mainstreamOnly = mainstream)

/**
 * The full grid under the rows: the popular titles of [target]
 */
fun gridQuery(target: BrowseTarget): TmdbDiscoverQuery? = baseQuery(target)?.popular(mainstream = target is BrowseTarget.Genre)

/**
 * Themed rows of a browse page, streaming-service style
 */
fun browseRows(
    target: BrowseTarget,
    today: LocalDate,
): List<BrowseRowSpec> {
    if (target is BrowseTarget.AnyCollection) {
        return listOf(BrowseRowSpec(BrowseRowKind.CollectionParts(target.id), ResStringProvider(R.string.browse_saga_all)))
    }
    if (target is BrowseTarget.Saga) {
        return listOf(BrowseRowSpec(BrowseRowKind.SagaParts(target.saga), ResStringProvider(R.string.browse_saga_all)))
    }
    val base = baseQuery(target) ?: return emptyList()
    val movie = target.mediaType == TmdbMediaType.MOVIE
    val brand = target !is BrowseTarget.Genre

    fun row(
        title: StringProvider,
        query: TmdbDiscoverQuery,
    ) = BrowseRowSpec(BrowseRowKind.Query(query), title)
    return buildList {
        add(row(ResStringProvider(R.string.discover_popular), base.popular(mainstream = !brand)))
        add(
            row(
                ResStringProvider(R.string.discover_top_rated),
                base.copy(sort = TmdbSort.RATING, minVotes = if (brand) TOP_MIN_VOTES_BRAND else TOP_MIN_VOTES),
            ),
        )
        add(
            row(
                ResStringProvider(R.string.browse_new),
                base.copy(
                    releasedAfter = today.minusYears(1),
                    minVotes = if (brand) NEW_MIN_VOTES_BRAND else base.minVotes,
                    mainstreamOnly = !brand,
                ),
            ),
        )
        add(
            row(
                ResStringProvider(R.string.browse_gems),
                base.copy(sort = TmdbSort.RATING, minRating = GEMS_MIN_RATING, minVotes = POPULAR_MIN_VOTES, maxVotes = GEMS_MAX_VOTES),
            ),
        )
        add(
            row(
                ResStringProvider(R.string.browse_classics),
                base.copy(releasedBefore = CLASSICS_BEFORE, minVotes = if (movie) CLASSICS_MIN_VOTES_MOVIE else CLASSICS_MIN_VOTES_TV),
            ),
        )
        if (target is BrowseTarget.Genre) {
            genreThemes(target.genre, target.mediaType).forEach { add(row(ResStringProvider(it.titleRes), it.query)) }
        }
        if (target is BrowseTarget.Genre && movie) {
            relatedStudios(target.genre).forEach { studio ->
                add(row(StringStringProvider(studio.displayName), base.copy(companyId = studio.companyId).popular()))
            }
        }
    }
}
