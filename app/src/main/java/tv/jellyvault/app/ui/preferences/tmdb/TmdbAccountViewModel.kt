package tv.jellyvault.app.ui.preferences.tmdb

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.account.AccountSnapshot
import tv.jellyvault.app.catalog.account.LinkRequest
import tv.jellyvault.app.catalog.account.TmdbAccountException
import tv.jellyvault.app.catalog.account.TmdbAccountRepository
import tv.jellyvault.app.catalog.account.TmdbAccountState
import tv.jellyvault.app.catalog.tmdb.TmdbAuth
import tv.jellyvault.app.catalog.tmdb.TmdbKeyStore
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.ui.preferences.arr.maskApiKey
import javax.inject.Inject

data class TmdbAccountUiState(
    val account: TmdbAccountState = TmdbAccountState.NotLinked,
    /** The approval page to open on a phone, while linking */
    val link: LinkRequest? = null,
    val busy: Boolean = false,
    @param:StringRes val error: Int? = null,
    /** What the app last read from the account, to tell whether ratings still reach the account */
    val received: ReceivedRatings? = null,
    /** The entered key, masked; null when none was entered */
    val maskedKey: String? = null,
    val usesBuildKey: Boolean = false,
)

/**
 * The ratings and watchlist read from TMDB: the newest movie and series ratings show whether the latest ratings
 * arrived
 */
data class ReceivedRatings(
    val ratings: Int,
    val watchlist: Int,
    val latest: List<String>,
    /** When the app read them, 0 while the first read runs */
    val readAt: Long,
)

internal fun AccountSnapshot.toReceived() =
    ReceivedRatings(
        ratings = ratings.size,
        watchlist = watchlist.size,
        // Movies then series, each most recently rated first
        latest =
            listOfNotNull(
                ratings.firstOrNull { it.item.mediaType == TmdbMediaType.MOVIE },
                ratings.firstOrNull { it.item.mediaType == TmdbMediaType.TV },
            ).map { it.item.title },
        readAt = at,
    )

@HiltViewModel
class TmdbAccountViewModel
    @Inject
    constructor(
        private val repository: TmdbAccountRepository,
        private val keyStore: TmdbKeyStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TmdbAccountUiState(account = repository.state.value))
        val state: StateFlow<TmdbAccountUiState> = _state

        init {
            viewModelScope.launch {
                repository.state.collect { account ->
                    _state.update { it.copy(account = account, received = null) }
                    if (account is TmdbAccountState.Linked) loadReceived()
                }
            }
            // The signed-in profile may not be the one that linked the account
            viewModelScope.launchIO { repository.refreshState() }
            viewModelScope.launchIO { publishKey() }
        }

        private fun publishKey() =
            _state.update { it.copy(maskedKey = maskApiKey(keyStore.userKey()), usesBuildKey = keyStore.usesBuildDefault) }

        /** An empty value clears the key; one TMDB could never accept (a character dropped by the keyboard) is refused */
        fun setKey(value: String) =
            viewModelScope.launchIO {
                val key = value.trim()
                if (key.isNotEmpty() && TmdbAuth.of(key) == null) {
                    _state.update { it.copy(error = R.string.tmdb_key_invalid) }
                    return@launchIO
                }
                keyStore.save(key)
                _state.update { it.copy(error = null) }
                publishKey()
            }

        private fun loadReceived() =
            viewModelScope.launchIO {
                // Nothing read yet on this device: the first read runs in the background, look again shortly
                repeat(FIRST_READ_CHECKS) {
                    val received = repository.snapshot().toReceived()
                    _state.update { if (it.account is TmdbAccountState.Linked) it.copy(received = received) else it }
                    if (received.readAt != 0L) return@launchIO
                    delay(FIRST_READ_WAIT_MS)
                }
            }

        fun startLink() =
            run(keepLink = false) {
                val link = repository.startLink()
                _state.update { it.copy(link = link) }
            }

        /**
         * The user approved the request on their phone
         */
        fun finishLink() {
            val token = _state.value.link?.token ?: return
            run(keepLink = true) {
                repository.finishLink(token)
                _state.update { it.copy(link = null) }
            }
        }

        fun cancelLink() = _state.update { it.copy(link = null, error = null) }

        fun unlink() = run(keepLink = false) { repository.unlink() }

        private fun run(
            keepLink: Boolean,
            block: suspend () -> Unit,
        ) {
            if (_state.value.busy) return
            _state.update { it.copy(busy = true, error = null, link = if (keepLink) it.link else null) }
            viewModelScope.launchIO {
                try {
                    block()
                } catch (e: TmdbAccountException) {
                    Timber.w(e, "TMDB account")
                    val error = if (e.unauthorized) R.string.tmdb_account_not_approved else R.string.tmdb_account_unreachable
                    _state.update { it.copy(error = error) }
                } finally {
                    _state.update { it.copy(busy = false) }
                }
            }
        }

        companion object {
            const val FIRST_READ_CHECKS = 10
            const val FIRST_READ_WAIT_MS = 3_000L
        }
    }
