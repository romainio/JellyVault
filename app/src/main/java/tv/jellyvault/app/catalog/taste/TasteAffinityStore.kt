package tv.jellyvault.app.catalog.taste

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import timber.log.Timber
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import java.io.File
import java.util.UUID

/** TMDB series genres and the movie genres they stand for */
private val SERIES_TO_MOVIE_GENRES =
    mapOf(
        10759 to listOf(28, 12),
        10765 to listOf(878, 14),
        10768 to listOf(10752),
        10762 to listOf(10751),
    )

/**
 * Share of each TMDB (movie) genre in what TMDB recommends after the user's titles: 1 for the most frequent
 * genre, weighted by how much each seed counts
 */
fun genreAffinity(results: List<SeedRecommendations>): Map<Int, Double> {
    val scores = HashMap<Int, Double>()
    results.forEach { (seed, items) ->
        items.forEach { item ->
            item.genreIds
                .flatMap { if (item.mediaType == TmdbMediaType.TV) SERIES_TO_MOVIE_GENRES[it] ?: listOf(it) else listOf(it) }
                .distinct()
                .forEach { scores[it] = (scores[it] ?: 0.0) + seed.weight }
        }
    }
    val max = scores.values.maxOrNull() ?: return emptyMap()
    return scores.mapValues { it.value / max }
}

/**
 * The last known genre affinity of each user, kept on disk so the home plan never waits on the network
 */
class TasteAffinityStore(
    private val dir: File,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(user: UUID): Map<Int, Double> = mutex.withLock { readAll()[user.toString()].orEmpty() }

    suspend fun save(
        user: UUID,
        affinity: Map<Int, Double>,
    ) = mutex.withLock {
        val all = readAll().toMutableMap()
        all[user.toString()] = affinity
        try {
            dir.mkdirs()
            File(dir, FILE).writeText(json.encodeToString(all))
        } catch (e: Exception) {
            Timber.w(e, "Could not save the taste affinity")
        }
    }

    private fun readAll(): Map<String, Map<Int, Double>> =
        try {
            File(dir, FILE)
                .takeIf { it.exists() }
                ?.readText()
                ?.let { json.decodeFromString<Map<String, Map<Int, Double>>>(it) }
                .orEmpty()
        } catch (e: Exception) {
            Timber.w(e, "Unreadable taste affinity, starting again")
            emptyMap()
        }

    companion object {
        const val FILE = "taste_affinity.json"
    }
}
