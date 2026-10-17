package tv.jellyvault.app.catalog

import androidx.annotation.StringRes
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
        /**
         * Rows shown above this one: their titles are left out, and the row is dropped when too few titles are left
         * (a studio's "top rated" is mostly its "popular" row again)
         */
        val exclude: List<TmdbDiscoverQuery> = emptyList(),
        /** TMDB pages of 20 titles: more for a row that must be complete (every Marvel film in order) */
        val pages: Int = 1,
        /** Only the first film of each saga (no "Part II" right after "Part I") */
        val onePerSaga: Boolean = false,
    ) : BrowseRowKind

    data class SagaParts(
        val saga: CatalogSaga,
    ) : BrowseRowKind
}

data class BrowseRowSpec(
    val kind: BrowseRowKind,
    val title: StringProvider,
    /** The row is named after its TMDB collection once loaded ("Avengers"), [title] until then */
    val titleFromCollection: Boolean = false,
)

private const val POPULAR_MIN_VOTES = 200
private const val POPULAR_MIN_RATING = 5.0
private const val TOP_MIN_VOTES = 1000
private const val TOP_MIN_VOTES_BRAND = 300

/** A studio or network rarely gets 200 votes on a title of the last 12 months */
private const val NEW_MIN_VOTES_BRAND = 50
private const val CLASSICS_MIN_VOTES_MOVIE = 500
private const val CLASSICS_MIN_VOTES_TV = 200
private val CLASSICS_BEFORE: LocalDate = LocalDate.of(1999, 12, 31)

/**
 * Hidden gems: much liked, known enough to be trusted, not the hits everyone has seen. Old enough for the rating to have
 * settled (fresh releases start high), French or English, no cartoons, documentaries or TV films among live-action genres.
 * Best known first: sorting by rating put niche titles with inflated ratings first (Gabriel's Inferno). Older films have
 * the Classics row.
 */
private const val GEMS_MIN_RATING_MOVIE = 7.2
private const val GEMS_MIN_VOTES_MOVIE = 1200
private const val GEMS_MAX_VOTES_MOVIE = 6000
private const val GEMS_MIN_RATING_TV = 7.5
private const val GEMS_MIN_VOTES_TV = 300
private const val GEMS_MAX_VOTES_TV = 2000
private const val GEMS_SETTLED_MONTHS = 18L
private val GEMS_LANGUAGES = listOf("fr", "en")
private val GEMS_SINCE: LocalDate = LocalDate.of(1990, 1, 1)

/** Animation has its own genre: kept out of the live-action genres (no anime among the dramas) */
private const val ANIMATION = 16
private const val FAMILY = 10751
private const val DOCUMENTARY = 99
private const val TV_MOVIE = 10770

/** Genres whose pages may show cartoons */
private val CARTOON_GENRES = setOf(CatalogGenre.ANIMATION, CatalogGenre.CARTOONS, CatalogGenre.FAMILY)

/** Genres left out of every row of a genre page */
private fun excludedGenres(genre: CatalogGenre): List<Int> = if (genre in CARTOON_GENRES) emptyList() else listOf(ANIMATION)

/**
 * The query every row of [target] starts from, null if the target has nothing for its type (or is a saga)
 */
private fun baseQuery(target: BrowseTarget): TmdbDiscoverQuery? =
    when (target) {
        is BrowseTarget.Genre -> {
            val id = if (target.mediaType == TmdbMediaType.MOVIE) target.genre.movieGenreId else target.genre.tvGenreId
            id?.let {
                TmdbDiscoverQuery(
                    target.mediaType,
                    genreId = it,
                    companyIds = target.genre.companyIds,
                    withoutGenreIds = excludedGenres(target.genre),
                )
            }
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
 * Hidden gems of a genre page, see [GEMS_MIN_RATING_MOVIE]
 */
internal fun gemsQuery(
    base: TmdbDiscoverQuery,
    genre: CatalogGenre,
    today: LocalDate,
): TmdbDiscoverQuery {
    val movie = base.mediaType == TmdbMediaType.MOVIE
    val ownGenre = base.genreId
    val withoutGenres =
        buildList {
            addAll(base.withoutGenreIds)
            add(DOCUMENTARY)
            add(TV_MOVIE)
            if (genre !in CARTOON_GENRES) add(FAMILY)
        }.filter { it != ownGenre }.distinct()
    return base.copy(
        sort = TmdbSort.VOTES,
        minRating = if (movie) GEMS_MIN_RATING_MOVIE else GEMS_MIN_RATING_TV,
        minVotes = if (movie) GEMS_MIN_VOTES_MOVIE else GEMS_MIN_VOTES_TV,
        maxVotes = if (movie) GEMS_MAX_VOTES_MOVIE else GEMS_MAX_VOTES_TV,
        releasedAfter = GEMS_SINCE,
        releasedBefore = today.minusMonths(GEMS_SETTLED_MONTHS),
        // Anime are the gems of animation
        originalLanguages = if (genre in CARTOON_GENRES) GEMS_LANGUAGES + "ja" else GEMS_LANGUAGES,
        withoutGenreIds = withoutGenres,
        mainstreamOnly = false,
    )
}

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
    if (target is BrowseTarget.Genre) return genreRows(target, base, today)
    val sagas = studioSagas(base.companyId).map(::sagaRow)
    return when (base.companyId) {
        DISNEY_COMPANY -> {
            disneyRows(base) + sagas
        }

        // What is popular, each saga, then every film in release order
        MARVEL_COMPANY -> {
            listOf(row(R.string.discover_popular, base.popular())) + sagas +
                row(
                    R.string.browse_release_order,
                    base.copy(
                        sort = TmdbSort.RELEASE_ASC,
                        releasedAfter = MCU_START,
                        minVotes = MCU_MIN_VOTES,
                        withoutGenreIds = listOf(ANIMATION),
                    ),
                    pages = RELEASE_ORDER_PAGES,
                )
        }

        else -> {
            brandRows(base, today) + sagas
        }
    }
}

private fun row(
    title: StringProvider,
    query: TmdbDiscoverQuery,
    exclude: List<TmdbDiscoverQuery> = emptyList(),
    pages: Int = 1,
) = BrowseRowSpec(BrowseRowKind.Query(query, exclude, pages), title)

private fun row(
    @StringRes title: Int,
    query: TmdbDiscoverQuery,
    exclude: List<TmdbDiscoverQuery> = emptyList(),
    pages: Int = 1,
) = row(ResStringProvider(title), query, exclude, pages)

private fun sagaRow(saga: StudioSaga) =
    BrowseRowSpec(BrowseRowKind.CollectionParts(saga.collectionId), StringStringProvider(saga.name), titleFromCollection = true)

private fun genreRows(
    target: BrowseTarget.Genre,
    base: TmdbDiscoverQuery,
    today: LocalDate,
): List<BrowseRowSpec> {
    val movie = target.mediaType == TmdbMediaType.MOVIE
    val popular = base.popular(mainstream = true)
    val top = base.copy(sort = TmdbSort.RATING, minVotes = TOP_MIN_VOTES, mainstreamOnly = true)
    val gems = gemsQuery(base, target.genre, today)
    val classics =
        base.copy(releasedBefore = CLASSICS_BEFORE, minVotes = if (movie) CLASSICS_MIN_VOTES_MOVIE else CLASSICS_MIN_VOTES_TV)
    return buildList {
        add(row(R.string.discover_popular, popular))
        add(row(R.string.discover_top_rated, top, exclude = listOf(popular)))
        add(row(R.string.browse_new, base.copy(releasedAfter = today.minusYears(1), mainstreamOnly = true)))
        add(
            BrowseRowSpec(
                BrowseRowKind.Query(gems, exclude = listOf(popular, top), onePerSaga = true),
                ResStringProvider(R.string.browse_gems),
            ),
        )
        add(row(R.string.browse_classics, classics))
        genreThemes(target.genre, target.mediaType).forEach { add(row(it.titleRes, it.query)) }
        if (movie) {
            relatedStudios(target.genre).forEach { studio ->
                add(row(StringStringProvider(studio.displayName), base.copy(companyId = studio.companyId).popular()))
            }
        }
    }
}

/**
 * A studio or network: popular, new, then top rated and classics without the titles already shown above
 */
private fun brandRows(
    base: TmdbDiscoverQuery,
    today: LocalDate,
): List<BrowseRowSpec> {
    val movie = base.mediaType == TmdbMediaType.MOVIE
    val popular = base.popular()
    val top = base.copy(sort = TmdbSort.RATING, minVotes = TOP_MIN_VOTES_BRAND)
    val classics =
        base.copy(releasedBefore = CLASSICS_BEFORE, minVotes = if (movie) CLASSICS_MIN_VOTES_MOVIE else CLASSICS_MIN_VOTES_TV)
    return listOf(
        row(R.string.discover_popular, popular),
        row(R.string.browse_new, base.copy(releasedAfter = today.minusYears(1), minVotes = NEW_MIN_VOTES_BRAND)),
        row(R.string.discover_top_rated, top, exclude = listOf(popular)),
        row(R.string.browse_classics, classics, exclude = listOf(popular, top)),
    )
}

/**
 * Walt Disney Pictures: one row per kind of Disney film rather than popular / top rated of everything
 */
private fun disneyRows(base: TmdbDiscoverQuery): List<BrowseRowSpec> {
    val disney = listOf(DISNEY_COMPANY, DISNEY_ANIMATION_COMPANY, DISNEY_PRODUCTIONS_COMPANY)
    val animation = TmdbDiscoverQuery(TmdbMediaType.MOVIE, genreId = ANIMATION, minVotes = DISNEY_MIN_VOTES)
    val liveAction = base.copy(withoutGenreIds = listOf(ANIMATION), minVotes = DISNEY_MIN_VOTES)
    return listOf(
        row(R.string.discover_popular, base.popular()),
        row(R.string.disney_animated_classics, animation.copy(companyIds = disney, releasedBefore = DISNEY_RENEWAL)),
        row(
            R.string.disney_recent_animation,
            animation.copy(companyIds = listOf(DISNEY_ANIMATION_COMPANY), releasedAfter = DISNEY_RENEWAL.plusDays(1)),
        ),
        row(StringStringProvider("Pixar"), TmdbDiscoverQuery(TmdbMediaType.MOVIE, companyId = PIXAR_COMPANY).popular()),
        row(
            R.string.disney_princesses,
            TmdbDiscoverQuery(
                TmdbMediaType.MOVIE,
                companyIds = disney,
                keywordIds = DISNEY_FAIRY_TALE_KEYWORDS,
                minVotes = DISNEY_MIN_VOTES,
            ),
        ),
        row(R.string.disney_live_remakes, liveAction.copy(keywordIds = DISNEY_REMAKE_KEYWORDS)),
        row(R.string.disney_live_adventures, liveAction.copy(withoutKeywordIds = DISNEY_REMAKE_KEYWORDS)),
    )
}

private const val DISNEY_COMPANY = 2
private const val DISNEY_ANIMATION_COMPANY = 6125

/** Walt Disney Productions: the studio's name until 1986, where Snow White and Cinderella are filed */
private const val DISNEY_PRODUCTIONS_COMPANY = 3166
private const val PIXAR_COMPANY = 3
private const val MARVEL_COMPANY = 420

/** The MCU: from Iron Man, without the cartoons, shorts and older co-productions also filed under Marvel Studios */
private val MCU_START: LocalDate = LocalDate.of(2008, 1, 1)
private const val MCU_MIN_VOTES = 1000

/** About 40 Marvel Studios films so far: 3 pages leave room for the next ones */
private const val RELEASE_ORDER_PAGES = 3
private const val DISNEY_MIN_VOTES = 300

/** Up to Chicken Little: the hand-drawn era */
private val DISNEY_RENEWAL: LocalDate = LocalDate.of(2005, 12, 31)

/** princess, fairy tale, based on fairy tale */
private val DISNEY_FAIRY_TALE_KEYWORDS = listOf(7376, 3205, 179411)

/** live action remake, remake */
private val DISNEY_REMAKE_KEYWORDS = listOf(245230, 9714)
