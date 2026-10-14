package tv.jellyvault.app.catalog.shelves

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import tv.jellyvault.app.catalog.taste.TasteAffinityStore
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.util.JvDispatchers
import java.io.File
import java.time.LocalDate
import java.util.UUID

enum class ShelfScope(
    val count: Int,
    val types: Set<TmdbMediaType>,
    val minSeries: Int,
) {
    HOME(16, setOf(TmdbMediaType.MOVIE, TmdbMediaType.TV), 4),
    MOVIES(12, setOf(TmdbMediaType.MOVIE), 0),
    SERIES(12, setOf(TmdbMediaType.TV), 0),
}

/**
 * Which styles a page shows, as shelf ids. Computed on the device today; a JellyVault server can send the same
 * list later without changing the pages.
 */
interface ShelfSource {
    suspend fun plan(
        scope: ShelfScope,
        today: LocalDate = LocalDate.now(),
    ): List<String>
}

/**
 * Plans with the user's last known taste, and freezes each plan for its period so the home only changes twice
 * a week
 */
class LocalShelfSource(
    private val dir: File,
    private val affinityStore: TasteAffinityStore,
    private val currentUser: () -> UUID?,
) : ShelfSource {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun plan(
        scope: ShelfScope,
        today: LocalDate,
    ): List<String> {
        val user = currentUser()
        val key = "${user ?: "none"}:${scope.name}:${ShelfPeriod.of(today).start}"
        // Small files, but never on the main thread
        return withContext(JvDispatchers.IO) { planLocked(key, user, scope, today) }
    }

    private suspend fun planLocked(
        key: String,
        user: UUID?,
        scope: ShelfScope,
        today: LocalDate,
    ): List<String> =
        mutex.withLock {
            val saved = readAll()
            saved[key]?.filter { ShelfCatalog.byId(it) != null }?.takeIf { it.isNotEmpty() }?.let { return@withLock it }
            val affinity = user?.let { affinityStore.load(it) }.orEmpty()
            val plan = planShelves(ShelfCatalog.ALL.filter { it.mediaType in scope.types }, today, affinity, scope.count, scope.minSeries)
            // Older periods are dropped: only the current plans are kept
            val period = ShelfPeriod.of(today).start.toString()
            write(saved.filterKeys { it.endsWith(period) } + (key to plan))
            plan
        }

    private fun readAll(): Map<String, List<String>> =
        try {
            File(dir, FILE)
                .takeIf { it.exists() }
                ?.readText()
                ?.let { json.decodeFromString<Map<String, List<String>>>(it) }
                .orEmpty()
        } catch (e: Exception) {
            Timber.w(e, "Unreadable shelf plans, planning again")
            emptyMap()
        }

    private fun write(plans: Map<String, List<String>>) {
        try {
            dir.mkdirs()
            File(dir, FILE).writeText(json.encodeToString(plans))
        } catch (e: Exception) {
            Timber.w(e, "Could not save the shelf plans")
        }
    }

    companion object {
        const val FILE = "shelf_plans.json"
    }
}
