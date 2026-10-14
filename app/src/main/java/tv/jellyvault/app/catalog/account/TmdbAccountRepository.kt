package tv.jellyvault.app.catalog.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import tv.jellyvault.app.catalog.key
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbLanguage
import tv.jellyvault.app.util.JvDispatchers
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType.MOVIE as M
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType.TV as T

sealed interface TmdbAccountState {
    data object NotLinked : TmdbAccountState

    data class Linked(
        val username: String?,
    ) : TmdbAccountState

    /** TMDB refused the saved session: link again */
    data object Expired : TmdbAccountState
}

data class LinkRequest(
    val token: String,
    val url: String,
)

@Serializable
data class SavedRating(
    val item: TmdbItem,
    val rating: Double,
)

/**
 * What the account says about the user's taste: ratings (most recent first, movies then series) and watchlist
 */
@Serializable
data class AccountSnapshot(
    val ratings: List<SavedRating> = emptyList(),
    val watchlist: List<TmdbItem> = emptyList(),
    val at: Long = 0,
    /** TMDB language the titles were read in: another interface language refreshes the snapshot */
    val language: String = "",
)

@Serializable
private data class SavedAccount(
    val sessionId: String,
    val accountId: Int,
    val username: String? = null,
    /** The Jellyfin user who linked it: other profiles never see these ratings or this watchlist */
    val ownerUserId: String? = null,
)

/**
 * The linked TMDB account, its session on this device (in no-backup storage, like the Radarr/Sonarr keys) and a
 * cache of its ratings and watchlist. Reading never blocks on the network for more than one refresh.
 */
class TmdbAccountRepository(
    private val api: TmdbAccountClient,
    private val dir: File,
    private val scope: CoroutineScope,
    private val currentUser: () -> UUID?,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile
    private var memory: AccountSnapshot? = null
    private val refreshing = AtomicBoolean(false)
    private val changes = AtomicLong(0)

    internal var now: () -> Long = System::currentTimeMillis

    private val _state = MutableStateFlow(stateOf(readAccount()))
    val state: StateFlow<TmdbAccountState> = _state

    private fun SavedAccount.isMine() = ownerUserId == currentUser()?.toString()

    private fun stateOf(account: SavedAccount?): TmdbAccountState =
        if (account != null && account.isMine()) TmdbAccountState.Linked(account.username) else TmdbAccountState.NotLinked

    /**
     * The account state for the Jellyfin user signed in now (it changes with the profile)
     */
    suspend fun refreshState(): TmdbAccountState {
        val account = withContext(JvDispatchers.IO) { readAccount() }
        if (_state.value != TmdbAccountState.Expired || account?.isMine() != true) _state.value = stateOf(account)
        return _state.value
    }

    /**
     * Changes whenever the ratings or the linked account change, so cached recommendations can be recomputed
     */
    fun version(): Long = changes.get()

    val isConfigured: Boolean get() = api.isConfigured

    suspend fun startLink(): LinkRequest {
        val token = api.createRequestToken()
        return LinkRequest(token, TmdbAccountClient.approvalUrl(token))
    }

    /**
     * Once the user approved the request on TMDB
     */
    suspend fun finishLink(token: String) {
        val session = api.createSession(token)
        val account = api.account(session)
        mutex.withLock {
            writeFile(ACCOUNT_FILE, json.encodeToString(SavedAccount(session, account.id, account.username, currentUser()?.toString())))
            File(dir, SNAPSHOT_FILE).delete()
            memory = null
        }
        changes.incrementAndGet()
        _state.value = TmdbAccountState.Linked(account.username)
        // Ratings ready before the next home load
        readAccount()?.let(::refreshInBackground)
    }

    suspend fun unlink() {
        mutex.withLock {
            File(dir, ACCOUNT_FILE).delete()
            File(dir, SNAPSHOT_FILE).delete()
            memory = null
        }
        changes.incrementAndGet()
        _state.value = TmdbAccountState.NotLinked
    }

    /**
     * Ratings and watchlist, never waiting on the network: from memory or disk, refreshed in the background when
     * old or missing. Empty when no account is linked for the signed-in user, or the session expired.
     */
    suspend fun snapshot(): AccountSnapshot {
        val account = withContext(JvDispatchers.IO) { readAccount() }?.takeIf { it.isMine() } ?: return AccountSnapshot()
        if (_state.value == TmdbAccountState.Expired) return AccountSnapshot()
        val cached = mutex.withLock { memory ?: withContext(JvDispatchers.IO) { readSnapshot() }?.also { memory = it } }
        if (cached == null || now() - cached.at >= TTL_MS || cached.language != TmdbLanguage.current().tag) refreshInBackground(account)
        return cached ?: AccountSnapshot()
    }

    /**
     * The user's rating of [item], from the last snapshot
     */
    suspend fun ratingOf(item: TmdbItem): Double? = snapshot().ratings.firstOrNull { it.item.key == item.key }?.rating

    /**
     * Rate [item] on the account (or remove the rating when [value] is null), and use it at once in the taste
     */
    suspend fun rate(
        item: TmdbItem,
        value: Double?,
    ) {
        val account =
            withContext(JvDispatchers.IO) { readAccount() }?.takeIf { it.isMine() } ?: throw TmdbAccountException("No TMDB account", 401)
        api.rate(item.mediaType, item.tmdbId, account.sessionId, value)
        mutex.withLock {
            val current = memory ?: withContext(JvDispatchers.IO) { readSnapshot() } ?: AccountSnapshot()
            val others = current.ratings.filterNot { it.item.key == item.key }
            val updated = current.copy(ratings = if (value == null) others else listOf(SavedRating(item, value)) + others)
            memory = updated
            writeFile(SNAPSHOT_FILE, json.encodeToString(updated))
        }
        changes.incrementAndGet()
    }

    private fun refreshInBackground(account: SavedAccount) {
        // One refresh at a time, however many rows ask
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                fetch(account)
            } finally {
                refreshing.set(false)
            }
        }
    }

    private suspend fun fetch(account: SavedAccount): AccountSnapshot? =
        try {
            val language = TmdbLanguage.current().tag
            val snapshot =
                coroutineScope {
                    val ratedMovies = async { api.rated(M, account.accountId, account.sessionId) }
                    val ratedSeries = async { api.rated(T, account.accountId, account.sessionId) }
                    val watchMovies = async { api.watchlist(M, account.accountId, account.sessionId) }
                    val watchSeries = async { api.watchlist(T, account.accountId, account.sessionId) }
                    AccountSnapshot(
                        ratings = (ratedMovies.await() + ratedSeries.await()).map { SavedRating(it.item, it.rating) },
                        watchlist = interleave(watchMovies.await(), watchSeries.await()),
                        at = now(),
                        language = language,
                    )
                }
            mutex.withLock {
                // Unlinked or linked again meanwhile: this snapshot belongs to the old account
                if (readAccount()?.sessionId != account.sessionId) return null
                memory = snapshot
                writeFile(SNAPSHOT_FILE, json.encodeToString(snapshot))
            }
            changes.incrementAndGet()
            snapshot
        } catch (e: CancellationException) {
            throw e
        } catch (e: TmdbAccountException) {
            if (e.unauthorized && readAccount()?.sessionId == account.sessionId) {
                Timber.w(e, "TMDB session expired")
                _state.value = TmdbAccountState.Expired
                changes.incrementAndGet()
            } else {
                Timber.w(e, "TMDB account refresh failed, keeping the last snapshot")
            }
            null
        }

    private fun readAccount(): SavedAccount? =
        readFile(ACCOUNT_FILE)?.let {
            runCatching { json.decodeFromString<SavedAccount>(it) }.getOrNull()
        }

    private fun readSnapshot(): AccountSnapshot? =
        readFile(SNAPSHOT_FILE)?.let {
            runCatching { json.decodeFromString<AccountSnapshot>(it) }.getOrNull()
        }

    private fun readFile(name: String): String? =
        try {
            File(dir, name).takeIf { it.exists() }?.readText()
        } catch (e: Exception) {
            Timber.w(e, "Could not read %s", name)
            null
        }

    private fun writeFile(
        name: String,
        content: String,
    ) {
        try {
            dir.mkdirs()
            File(dir, name).writeText(content)
        } catch (e: Exception) {
            Timber.w(e, "Could not write %s", name)
        }
    }

    companion object {
        const val TTL_MS = 6 * 60 * 60 * 1000L
        const val ACCOUNT_FILE = "tmdb_account.json"
        const val SNAPSHOT_FILE = "tmdb_account_snapshot.json"

        private fun <X> interleave(
            first: List<X>,
            second: List<X>,
        ): List<X> =
            buildList {
                for (i in 0 until maxOf(first.size, second.size)) {
                    first.getOrNull(i)?.let(::add)
                    second.getOrNull(i)?.let(::add)
                }
            }
    }
}
