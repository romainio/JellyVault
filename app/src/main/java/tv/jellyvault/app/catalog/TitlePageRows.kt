package tv.jellyvault.app.catalog

import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbDetails
import tv.jellyvault.app.catalog.tmdb.TmdbDiscoverQuery
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbNamed
import tv.jellyvault.app.catalog.tmdb.mainstream

private const val ANIMATION = 16

/** TMDB recommends a few titles nobody has heard of: below this many votes they are left out */
private const val SIMILAR_MIN_VOTES_MOVIE = 300

/** Series collect fewer votes, French ones especially */
private const val SIMILAR_MIN_VOTES_TV = 80

/** A director with fewer other films than this gets the genre row instead */
internal const val DIRECTOR_MIN_TITLES = 3
private const val DIRECTOR_MIN_VOTES = 50
private const val SAME_GENRE_MIN_VOTES_MOVIE = 500
private const val SAME_GENRE_MIN_VOTES_TV = 150

/**
 * The rows under a TMDB title page, each about something else: the rest of its saga, titles TMDB recommends after it,
 * then the director's other films (or more of the same genres)
 */
data class TitlePageRows(
    val saga: List<MediaCard> = emptyList(),
    val similar: List<MediaCard> = emptyList(),
    val more: List<MediaCard> = emptyList(),
    /** Who [more] is about; null when it is the same-genre row */
    val director: TmdbNamed? = null,
)

/**
 * TMDB recommendations that fit [details]: no cartoon after a live-action film, no title nobody knows, nothing already
 * shown in another row
 */
internal fun fittingRecommendations(
    details: TmdbDetails,
    candidates: List<TmdbItem>,
    shown: Set<TmdbKey>,
): List<TmdbItem> {
    val cartoon = ANIMATION in details.genreIds
    return candidates
        .filter { it.key != details.item.key && it.key !in shown }
        .filter { cartoon || ANIMATION !in it.genreIds }
        .filter { it.voteCount >= if (it.mediaType == TmdbMediaType.MOVIE) SIMILAR_MIN_VOTES_MOVIE else SIMILAR_MIN_VOTES_TV }
        .mainstream()
}

/**
 * Popular titles sharing the first two genres of [details] (a crime drama gets crime dramas)
 */
internal fun sameGenreQuery(details: TmdbDetails): TmdbDiscoverQuery? {
    val genres = details.genreIds.take(2)
    val first = genres.firstOrNull() ?: return null
    val movie = details.item.mediaType == TmdbMediaType.MOVIE
    return TmdbDiscoverQuery(
        details.item.mediaType,
        genreId = first,
        andGenreIds = genres.drop(1),
        minVotes = if (movie) SAME_GENRE_MIN_VOTES_MOVIE else SAME_GENRE_MIN_VOTES_TV,
        minRating = 6.0,
        withoutGenreIds = if (ANIMATION in genres) emptyList() else listOf(ANIMATION),
        mainstreamOnly = true,
    )
}

/** The films of a director that are known enough to suggest */
internal fun List<TmdbItem>.knownFilms() = filter { it.voteCount >= DIRECTOR_MIN_VOTES }
