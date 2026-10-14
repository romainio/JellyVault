package tv.jellyvault.app.catalog.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import tv.jellyvault.app.catalog.tmdb.TmdbAuth
import tv.jellyvault.app.catalog.tmdb.TmdbCredential
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.services.hilt.IoCoroutineScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TmdbKey(
    val mediaType: TmdbMediaType,
    val tmdbId: Int,
)

/**
 * Maps TMDB IDs to the current user's Jellyfin items so catalog rows know what is already owned
 */
@Singleton
class LibraryIndex
    @Inject
    constructor(
        private val source: LibraryItemSource,
        serverRepository: ServerRepository,
        @param:IoCoroutineScope private val scope: CoroutineScope,
        private val tmdbCredential: TmdbCredential,
    ) {
        /**
         * The index is only used by TMDB rows: don't load the whole library while TMDB is not configured.
         * Read on every use because the key can be entered or cleared at runtime (Settings).
         */
        private val enabled: Boolean
            get() = TmdbAuth.of(tmdbCredential.current()) != null

        private val _index = MutableStateFlow<Map<TmdbKey, UUID>?>(null)

        /**
         * The index, or null if it has not been loaded yet for the current user
         */
        val index: StateFlow<Map<TmdbKey, UUID>?> = _index

        private val mutex = Mutex()
        private var lastLoadMs = 0L

        /** Time of the last refresh attempt, successful or not, so a failed first load can be retried */
        @Volatile
        private var lastAttemptMs = 0L
        private var hasUser = false

        /**
         * The user id that [refresh] should currently be loading for. Set synchronously whenever
         * the current user changes (see [init]), and read back at the end of [refresh] so that a
         * refresh which was in flight (or queued on [mutex]) when the user changed never publishes
         * data for the wrong user - see the "stale refresh" scenario in
         * [LibraryIndexTest.stale_refresh_does_not_overwrite_newer_user_index].
         */
        @Volatile
        private var currentUserId: UUID? = null

        internal var now: () -> Long = System::currentTimeMillis

        init {
            startTrackingUser(serverRepository)
        }

        private fun startTrackingUser(serverRepository: ServerRepository) {
            scope.launch {
                serverRepository.currentUserFlow
                    .map { it?.id }
                    .distinctUntilChanged()
                    .collectLatest { userId ->
                        currentUserId = userId
                        _index.value = null
                        lastLoadMs = 0L
                        hasUser = userId != null
                        if (userId != null && enabled) {
                            lastAttemptMs = now()
                            refresh()
                        } else {
                            // Nothing was attempted: a key entered later must trigger a load at once, not after the backoff
                            lastAttemptMs = 0L
                        }
                    }
            }
        }

        suspend fun refresh() {
            if (!enabled) return
            // Captured before acquiring the lock so a refresh started for the previous user - whether
            // triggered by the collector above or by the stale-refresh path in awaitIndex() - can tell,
            // once it (eventually) finishes, whether the user it loaded data for is still the current one.
            val targetUserId = currentUserId
            mutex.withLock {
                try {
                    val map = HashMap<TmdbKey, UUID>()
                    load(BaseItemKind.MOVIE, TmdbMediaType.MOVIE, map)
                    load(BaseItemKind.SERIES, TmdbMediaType.TV, map)
                    if (currentUserId == targetUserId) {
                        _index.value = map
                        lastLoadMs = now()
                        Timber.d("Library index loaded: %s items", map.size)
                    } else {
                        Timber.d("Discarding library index refresh for user %s: no longer current", targetUserId)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load library index, keeping previous one")
                } finally {
                    lastAttemptMs = now()
                }
            }
        }

        /**
         * Returns the index, waiting up to [timeoutMs] for the first load. Triggers a background refresh if
         * stale, or if no index could be loaded yet and the last attempt is older than [RETRY_BACKOFF_MS].
         */
        suspend fun awaitIndex(timeoutMs: Long = 3000): Map<TmdbKey, UUID> {
            if (!enabled) return emptyMap()
            if (hasUser && !mutex.isLocked) {
                val loaded = _index.value != null
                val stale = loaded && now() - lastLoadMs > STALE_MS
                val retryFailed = !loaded && now() - lastAttemptMs > RETRY_BACKOFF_MS
                if (stale || retryFailed) {
                    scope.launch { refresh() }
                }
            }
            return withTimeoutOrNull(timeoutMs) { index.filterNotNull().first() }
                ?: _index.value.orEmpty()
        }

        private suspend fun load(
            kind: BaseItemKind,
            mediaType: TmdbMediaType,
            into: MutableMap<TmdbKey, UUID>,
        ) {
            var start = 0
            while (true) {
                val page = source.fetchProviderIdPage(kind, start, PAGE_SIZE)
                page.items.forEach { item ->
                    parseTmdbId(item.providerIds)?.let { into[TmdbKey(mediaType, it)] = item.id }
                }
                start += page.items.size
                if (page.items.isEmpty() || start >= page.totalRecordCount) break
            }
        }

        companion object {
            const val PAGE_SIZE = 500
            const val STALE_MS = 15 * 60 * 1000L
            const val RETRY_BACKOFF_MS = 30 * 1000L

            fun parseTmdbId(providerIds: Map<String, String?>?): Int? =
                providerIds
                    ?.entries
                    ?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }
                    ?.value
                    ?.trim()
                    ?.toIntOrNull()
        }
    }
