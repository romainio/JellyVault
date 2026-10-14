package tv.jellyvault.app.catalog.taste

import tv.jellyvault.app.catalog.account.SavedRating
import tv.jellyvault.app.catalog.key
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import kotlin.math.pow

/**
 * A title the user played, most recently played first in the list that carries it
 */
data class PlayedTitle(
    val key: TmdbKey,
    val title: String,
)

/**
 * The user's taste: [seeds] drive the recommendations, [watched] are never recommended again
 */
data class TasteHistory(
    val seeds: List<TasteSeed>,
    val watched: Set<TmdbKey>,
    /** Titles rated 5 or less: what TMDB recommends after them ranks lower */
    val negative: List<TasteSeed> = emptyList(),
    /** Titles rated 8 or more, for the genre affinity of the home styles */
    val liked: List<TmdbItem> = emptyList(),
)

const val MAX_TASTE_SEEDS = 16
const val MAX_NEGATIVE_SEEDS = 4
private const val LIKED_MIN_RATING = 8.0
private const val DISLIKED_MAX_RATING = 5.0

/** A 10/10 weighs 1.5 (more than the last watched title), an 8/10 0.5 */
private const val RATING_SEED_SCALE = 1.5

/**
 * Add the account's ratings ([ratings] most recent first) to a Jellyfin history: titles rated 8 or more become
 * seeds weighted by the rating and its recency, titles rated 5 or less become negative seeds, and every rated
 * title counts as watched. A title in both keeps its heavier weight.
 */
fun TasteHistory.withRatings(
    ratings: List<SavedRating>,
    mediaType: TmdbMediaType,
    maxSeeds: Int = MAX_TASTE_SEEDS,
): TasteHistory {
    val mine = ratings.filter { it.item.mediaType == mediaType }.distinctBy { it.item.key }
    if (mine.isEmpty()) return this
    val liked = mine.filter { it.rating >= LIKED_MIN_RATING }
    val fromRatings =
        liked.mapIndexed { index, r ->
            val weight = (r.rating - 7) / 3 * RATING_SEED_SCALE * RECENCY_DECAY.pow(index)
            TasteSeed(r.item.key, r.item.title, weight)
        }
    val merged =
        (seeds + fromRatings)
            .groupBy { it.key }
            .map { (_, same) -> same.maxBy { it.weight } }
            .sortedByDescending { it.weight }
            .take(maxSeeds)
    val disliked =
        mine.filter { it.rating <= DISLIKED_MAX_RATING }.take(MAX_NEGATIVE_SEEDS).map {
            TasteSeed(it.item.key, it.item.title, (DISLIKED_MAX_RATING + 1 - it.rating) / DISLIKED_MAX_RATING)
        }
    return copy(
        seeds = merged,
        watched = watched + mine.map { it.item.key },
        negative = disliked,
        liked = liked.map { it.item },
    )
}

/** Each older title weighs this much less than the one played right after it */
private const val RECENCY_DECAY = 0.92
private const val FAVORITE_BOOST = 1.6

/** A favorite that was never played counts less than anything recently watched: favorites can be a watchlist */
private const val UNPLAYED_FAVORITE_WEIGHT = 0.5

fun buildTasteHistory(
    playedNewestFirst: List<PlayedTitle>,
    favorites: Set<TmdbKey>,
    maxSeeds: Int,
    favoriteTitles: Map<TmdbKey, String> = emptyMap(),
): TasteHistory {
    var weight = 1.0
    val seeds = mutableListOf<TasteSeed>()
    // The same title in two libraries, or watched twice, is one seed
    playedNewestFirst.distinctBy { it.key }.forEach { played ->
        val boost = if (played.key in favorites) FAVORITE_BOOST else 1.0
        seeds += TasteSeed(played.key, played.title, weight * boost)
        weight *= RECENCY_DECAY
    }
    val playedKeys = playedNewestFirst.map { it.key }.toSet()
    favorites.filter { it !in playedKeys }.forEach {
        seeds += TasteSeed(it, favoriteTitles[it] ?: "", UNPLAYED_FAVORITE_WEIGHT)
    }
    return TasteHistory(
        seeds = seeds.sortedByDescending { it.weight }.take(maxSeeds),
        watched = playedKeys + favorites,
    )
}
