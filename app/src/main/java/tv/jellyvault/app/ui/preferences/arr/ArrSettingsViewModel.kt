package tv.jellyvault.app.ui.preferences.arr

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.arr.ArrConfig
import tv.jellyvault.app.catalog.arr.ArrException
import tv.jellyvault.app.catalog.arr.ArrService
import tv.jellyvault.app.catalog.arr.ArrSettingsRepository
import tv.jellyvault.app.catalog.arr.QualityProfile
import tv.jellyvault.app.catalog.arr.RadarrClient
import tv.jellyvault.app.catalog.arr.RootFolder
import tv.jellyvault.app.catalog.arr.SonarrClient
import tv.jellyvault.app.catalog.arr.SystemStatus
import tv.jellyvault.app.ui.launchIO
import javax.inject.Inject

@HiltViewModel
class ArrSettingsViewModel
    @Inject
    constructor(
        private val repository: ArrSettingsRepository,
        private val radarr: RadarrClient,
        private val sonarr: SonarrClient,
    ) : ViewModel() {
        sealed interface Connection {
            data object Unknown : Connection

            data object Testing : Connection

            data class Connected(
                val version: String,
            ) : Connection

            data class Failed(
                @param:StringRes val message: Int,
                val code: Int? = null,
            ) : Connection
        }

        data class State(
            val service: ArrService = ArrService.RADARR,
            val config: ArrConfig = ArrConfig(),
            val connection: Connection = Connection.Unknown,
            val profiles: List<QualityProfile> = emptyList(),
            val folders: List<RootFolder> = emptyList(),
        )

        private val _state = MutableStateFlow(State())
        val state: StateFlow<State> = _state

        private var initializedFor: ArrService? = null

        fun init(service: ArrService) {
            if (initializedFor == service) return
            initializedFor = service
            _state.update { State(service = service) }
            viewModelScope.launchIO {
                repository.config(service).collect { config -> _state.update { it.copy(config = config) } }
            }
        }

        fun setUrl(value: String) =
            edit { current ->
                val url = normalizeArrUrl(value)
                if (url == current.url) {
                    current
                } else {
                    // Profiles and folders belong to the previous server
                    current.copy(url = url, qualityProfileId = 0, rootFolderPath = "")
                }
            }

        fun setApiKey(value: String) = edit { it.copy(apiKey = value.trim()) }

        fun setBasicUser(value: String) = edit { it.copy(basicUser = value.trim()) }

        fun setBasicPassword(value: String) = edit { it.copy(basicPassword = value) }

        fun selectProfile(id: Int) = save { it.copy(qualityProfileId = id) }

        fun selectFolder(path: String) = save { it.copy(rootFolderPath = path) }

        fun testConnection() {
            val service = _state.value.service
            viewModelScope.launchIO {
                _state.update { it.copy(connection = Connection.Testing) }
                val config = repository.current(service)
                val client = client(service)
                try {
                    val version = client.systemStatus(config).version
                    val profiles = client.qualityProfiles(config)
                    val folders = client.rootFolders(config)
                    _state.update { it.copy(connection = Connection.Connected(version), profiles = profiles, folders = folders) }
                } catch (e: ArrException) {
                    _state.update { it.copy(connection = Connection.Failed(e.messageRes(), (e as? ArrException.Http)?.code)) }
                }
            }
        }

        private fun edit(transform: (ArrConfig) -> ArrConfig) {
            _state.update { it.copy(connection = Connection.Unknown) }
            save(transform)
        }

        private fun save(transform: (ArrConfig) -> ArrConfig) {
            val service = _state.value.service
            viewModelScope.launchIO { repository.update(service, transform) }
        }

        private fun client(service: ArrService) =
            when (service) {
                ArrService.RADARR -> Client(radarr::systemStatus, radarr::qualityProfiles, radarr::rootFolders)
                ArrService.SONARR -> Client(sonarr::systemStatus, sonarr::qualityProfiles, sonarr::rootFolders)
            }

        private class Client(
            val systemStatus: suspend (ArrConfig?) -> SystemStatus,
            val qualityProfiles: suspend (ArrConfig?) -> List<QualityProfile>,
            val rootFolders: suspend (ArrConfig?) -> List<RootFolder>,
        )
    }

@StringRes
fun ArrException.messageRes(): Int =
    when (this) {
        is ArrException.NotConfigured -> R.string.arr_error_not_configured
        is ArrException.Unauthorized -> if (basicAuth) R.string.arr_error_basic_auth else R.string.arr_error_unauthorized
        is ArrException.InvalidSettings -> R.string.arr_error_invalid_settings
        is ArrException.Unreachable -> R.string.arr_error_unreachable
        is ArrException.Http -> R.string.arr_error_http
        is ArrException.InvalidResponse -> R.string.arr_error_invalid
    }

/**
 * Trim and add https:// when no scheme was typed
 */
fun normalizeArrUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.contains("://")) return trimmed
    return "https://$trimmed"
}
