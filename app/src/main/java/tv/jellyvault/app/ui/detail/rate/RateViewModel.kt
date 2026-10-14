package tv.jellyvault.app.ui.detail.rate

import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.account.TmdbAccountException
import tv.jellyvault.app.catalog.account.TmdbAccountRepository
import tv.jellyvault.app.catalog.account.TmdbAccountState
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.ui.launchIO
import javax.inject.Inject

data class RateState(
    /** "Rate from the TV" is on in the settings */
    val enabled: Boolean = false,
    /** The user's rating, 1 to 10 */
    val current: Int? = null,
    val sending: Boolean = false,
    @param:StringRes val error: Int? = null,
    /** Known once loaded from TMDB, for Jellyfin pages */
    val imdbId: String? = null,
    val title: String? = null,
    /** Set after a successful rating, for the confirmation banner */
    val confirmation: RateConfirmation? = null,
)

/** [value] is null when the rating was removed */
data class RateConfirmation(
    val value: Int?,
)

/**
 * Rating a title from the TV: off unless turned on in the settings, since ratings normally come from IMDb
 */
@HiltViewModel
class RateViewModel
    @Inject
    constructor(
        private val preferences: DataStore<AppPreferences>,
        private val account: TmdbAccountRepository,
        private val tmdb: TmdbClient,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RateState())
        val state: StateFlow<RateState> = _state
        private var item: TmdbItem? = null

        init {
            viewModelScope.launch {
                preferences.data.collect { prefs -> _state.update { it.copy(enabled = prefs.interfacePreferences.rateFromTv) } }
            }
        }

        fun init(item: TmdbItem) {
            if (this.item == item) return
            this.item = item
            viewModelScope.launchIO {
                val current = account.ratingOf(item)?.toInt()
                _state.update { it.copy(current = current) }
            }
        }

        /**
         * From a Jellyfin page, which only knows the TMDB id: the title and its IMDb id come from TMDB (cached)
         */
        fun init(
            mediaType: TmdbMediaType,
            tmdbId: Int,
        ) {
            if (item?.let { it.mediaType == mediaType && it.tmdbId == tmdbId } == true) return
            viewModelScope.launchIO {
                try {
                    val details = tmdb.details(mediaType, tmdbId)
                    item = details.item
                    val current = account.ratingOf(details.item)?.toInt()
                    _state.update { it.copy(current = current, imdbId = details.imdbId, title = details.item.title) }
                } catch (e: TmdbException) {
                    Timber.w(e, "No TMDB title %s to rate", tmdbId)
                }
            }
        }

        /**
         * Rate 1 to 10, or remove the rating with null
         */
        fun rate(value: Int?) {
            val item = item ?: return
            if (_state.value.sending) return
            _state.update { it.copy(sending = true, error = null) }
            viewModelScope.launchIO {
                try {
                    if (account.refreshState() !is TmdbAccountState.Linked) throw TmdbAccountException("Not linked", 401)
                    account.rate(item, value?.toDouble())
                    _state.update { it.copy(current = value, confirmation = RateConfirmation(value)) }
                } catch (e: TmdbAccountException) {
                    Timber.w(e, "Rating %s", item.tmdbId)
                    _state.update { it.copy(error = if (e.unauthorized) R.string.rate_needs_account else R.string.rate_failed) }
                } finally {
                    _state.update { it.copy(sending = false) }
                }
            }
        }

        fun clearError() = _state.update { it.copy(error = null) }

        fun consumeConfirmation() = _state.update { it.copy(confirmation = null) }
    }
