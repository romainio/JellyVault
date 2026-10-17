package tv.jellyvault.app.catalog.taste

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import tv.jellyvault.app.catalog.tmdb.TmdbNamed
import java.io.File

@Serializable
private data class StoredDirector(
    val id: Int,
    val name: String,
)

/**
 * The director of each movie the user liked, kept on disk: a film's director never changes, so "Vos réalisateurs"
 * only asks TMDB about films rated since the last time instead of every liked film at each start
 */
class DirectorStore(
    private val dir: File,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var memory: MutableMap<Int, StoredDirector?>? = null

    /**
     * The director of each of [movieIds], asking [fetch] only for the ones never looked up (null: TMDB has none)
     */
    suspend fun directors(
        movieIds: List<Int>,
        fetch: suspend (List<Int>) -> Map<Int, TmdbNamed?>,
    ): List<TmdbNamed?> {
        val known = mutex.withLock { all() }
        val missing = movieIds.filter { it !in known }
        if (missing.isNotEmpty()) {
            val fetched = fetch(missing)
            mutex.withLock {
                val all = all()
                // A failed lookup is not in [fetched]: it is tried again next time
                fetched.forEach { (movie, director) -> all[movie] = director?.let { StoredDirector(it.id, it.name) } }
                save(all)
            }
        }
        val all = mutex.withLock { all() }
        return movieIds.map { id -> all[id]?.let { TmdbNamed(it.id, it.name) } }
    }

    private fun all(): MutableMap<Int, StoredDirector?> =
        memory ?: try {
            File(dir, FILE)
                .takeIf { it.exists() }
                ?.readText()
                ?.let { json.decodeFromString<Map<Int, StoredDirector?>>(it) }
                .orEmpty()
                .toMutableMap()
        } catch (e: Exception) {
            Timber.w(e, "Unreadable directors, starting again")
            mutableMapOf()
        }.also { memory = it }

    private fun save(all: Map<Int, StoredDirector?>) {
        try {
            dir.mkdirs()
            File(dir, FILE).writeText(json.encodeToString(all))
        } catch (e: Exception) {
            Timber.w(e, "Could not save the directors")
        }
    }

    companion object {
        const val FILE = "liked_directors.json"
    }
}
