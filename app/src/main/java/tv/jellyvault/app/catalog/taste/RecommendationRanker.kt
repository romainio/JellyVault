package tv.jellyvault.app.catalog.taste

import tv.jellyvault.app.catalog.key
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbNamed
import tv.jellyvault.app.catalog.tmdb.isMainstream

/**
 * A title the user watched or liked, with how much it should count (recent and favorite titles count more)
 */
data class TasteSeed(
    val key: TmdbKey,
    val title: String,
    val weight: Double,
)

/**
 * What TMDB recommends after one [seed], best first
 */
data class SeedRecommendations(
    val seed: TasteSeed,
    val items: List<TmdbItem>,
)

data class BecauseRow(
    val seed: TasteSeed,
    val items: List<TmdbItem>,
)

private const val MIN_RATING = 6.0
private const val MIN_VOTES = 100
private const val RATING_TIE_BREAK = 0.05

/** A title at the end of a seed's list counts half as much as the first one */
private const val POSITION_DECAY = 0.5

/** What TMDB recommends after a disliked title counts against it, at this share */
private const val NEGATIVE_FACTOR = 0.7

private fun TmdbItem.isGoodEnough(
    minRating: Double = MIN_RATING,
    minVotes: Int = MIN_VOTES,
) = (voteAverage ?: 0.0) >= minRating && voteCount >= minVotes && isMainstream()

private fun scoreAll(results: List<SeedRecommendations>): Map<TmdbKey, Double> {
    val scores = HashMap<TmdbKey, Double>()
    results.forEach { (seed, recommended) ->
        val size = recommended.size.coerceAtLeast(1)
        recommended.forEachIndexed { position, item ->
            scores[item.key] = (scores[item.key] ?: 0.0) + seed.weight * (1.0 - POSITION_DECAY * position / size)
        }
    }
    return scores
}

/**
 * "Pour toi": titles recommended by several of the user's seeds rank first. Already watched titles and the seeds
 * themselves are left out, as are low-rated or obscure ones.
 */
fun rankForYou(
    results: List<SeedRecommendations>,
    exclude: Set<TmdbKey>,
    limit: Int,
    negative: List<SeedRecommendations> = emptyList(),
    minRating: Double = MIN_RATING,
    minVotes: Int = MIN_VOTES,
): List<TmdbItem> {
    val seeds = (results + negative).map { it.seed.key }.toSet()
    val scores = LinkedHashMap<TmdbKey, Double>()
    val items = HashMap<TmdbKey, TmdbItem>()
    results.forEach { (seed, recommended) ->
        val size = recommended.size.coerceAtLeast(1)
        recommended.forEachIndexed { position, item ->
            val key = item.key
            if (key in exclude || key in seeds || !item.isGoodEnough(minRating, minVotes)) return@forEachIndexed
            items.putIfAbsent(key, item)
            scores[key] = (scores[key] ?: 0.0) + seed.weight * (1.0 - POSITION_DECAY * position / size)
        }
    }
    val against = scoreAll(negative)
    return scores.entries
        .map { (key, score) -> key to score - NEGATIVE_FACTOR * (against[key] ?: 0.0) }
        // Mostly recommended after disliked titles: out
        .filter { it.second > 0 }
        // Stable sort: ties keep the order in which they were first seen
        .sortedByDescending { (key, score) -> score + RATING_TIE_BREAK * (items.getValue(key).voteAverage ?: 0.0) }
        .take(limit)
        .map { items.getValue(it.first) }
}

/**
 * "Parce que tu as regardé X": one row per heaviest seed, never showing a title twice. A seed with fewer than
 * [minItems] new titles gets no row.
 */
fun becauseRows(
    results: List<SeedRecommendations>,
    exclude: Set<TmdbKey>,
    maxRows: Int,
    minItems: Int,
    perRow: Int,
): List<BecauseRow> {
    val used = HashSet<TmdbKey>()
    val rows = mutableListOf<BecauseRow>()
    for ((seed, recommended) in results.sortedByDescending { it.seed.weight }) {
        if (rows.size >= maxRows) break
        val fresh = recommended.filter { it.key !in exclude && it.key !in used && it.key != seed.key && it.isGoodEnough() }.take(perRow)
        if (fresh.size < minItems) continue
        used += fresh.map { it.key }
        rows += BecauseRow(seed, fresh)
    }
    return rows
}

/**
 * The directors behind most of [likedDirectors] (one entry per liked film, null when unknown), at least [minLiked] films
 * each, most liked first and the most recent one on ties
 */
fun rankDirectors(
    likedDirectors: List<TmdbNamed?>,
    max: Int,
    minLiked: Int,
): List<TmdbNamed> =
    likedDirectors
        .filterNotNull()
        .groupBy { it.id }
        .values
        .filter { it.size >= minLiked }
        // groupBy keeps the first appearance order: a stable sort keeps the most recent first on ties
        .sortedByDescending { it.size }
        .take(max)
        .map { it.first() }

/**
 * The directors' films, one of each in turn (most popular first), without what the user has seen or nobody knows
 */
fun mixDirectorFilms(
    films: List<List<TmdbItem>>,
    watched: Set<TmdbKey>,
    minVotes: Int,
): List<TmdbItem> {
    val lists = films.map { list -> list.filter { it.key !in watched && it.voteCount >= minVotes } }
    val longest = lists.maxOfOrNull { it.size } ?: 0
    return (0 until longest).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }.distinctBy { it.key }
}
