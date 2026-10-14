package tv.jellyvault.app.ui.preferences

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.JellyfinUser
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.preferences.resetSubtitles
import tv.jellyvault.app.preferences.updateSubtitlePreferences
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.services.Release
import tv.jellyvault.app.services.ScreensaverService
import tv.jellyvault.app.services.ServerReportService
import tv.jellyvault.app.services.UpdateChecker
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.util.DataLoadingState
import tv.jellyvault.app.util.ExceptionHandler
import tv.jellyvault.app.util.LoadingState
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        val screensaverService: ScreensaverService,
        private val serverRepository: ServerRepository,
        private val updateChecker: UpdateChecker,
        private val serverReportService: ServerReportService,
    ) : ViewModel() {
        val currentUser =
            serverRepository.currentUserFlow.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null,
            )
        val currentUserDto =
            serverRepository.currentUserDtoFlow.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null,
            )

        private val _quickConnectStatus = MutableStateFlow<LoadingState>(LoadingState.Pending)
        val quickConnectStatus: StateFlow<LoadingState> = _quickConnectStatus

        val releaseNotes = MutableStateFlow<DataLoadingState<Release>>(DataLoadingState.Pending)

        val externalPlayers = MutableStateFlow<List<ExternalPlayerApp>>(emptyList())

        init {
            viewModelScope.launchIO {
                val fakeIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType("https://example.com/video.mp4".toUri(), "video/*")
                    }
                val externalPlayers = getExternalPlayers(context)
                val systemDefault =
                    ExternalPlayerApp(
                        name = context.getString(R.string.system_default),
                        icon = null,
                        identifier = "",
                    )
                this@PreferencesViewModel.externalPlayers.update { listOf(systemDefault) + externalPlayers }
            }
        }

        fun sendAppLogs() {
            viewModelScope.launchIO { serverReportService.sendAppLogs() }
        }

        fun resetSubtitleSettings() {
            viewModelScope.launchIO {
                resetSubtitleSettings(preferenceDataStore)
            }
        }

        fun setPin(
            user: JellyfinUser,
            pin: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                serverRepository.updateUserAuth(user, pin, false)
            }
        }

        fun setRequireLogin(user: JellyfinUser) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                serverRepository.updateUserAuth(user, null, true)
            }
        }

        fun removeLoginAndPin(user: JellyfinUser) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                serverRepository.updateUserAuth(user, null, false)
            }
        }

        fun resetQuickConnectStatus() {
            _quickConnectStatus.value = LoadingState.Pending
        }

        fun authorizeQuickConnect(code: String) {
            viewModelScope.launchIO {
                _quickConnectStatus.value = LoadingState.Loading
                try {
                    val success = serverRepository.authorizeQuickConnect(code)
                    _quickConnectStatus.value =
                        if (success) {
                            LoadingState.Success
                        } else {
                            LoadingState.Error("Authorization failed")
                        }
                } catch (e: Exception) {
                    _quickConnectStatus.value = LoadingState.Error(e)
                }
            }
        }

        fun fetchReleaseNotes() {
            viewModelScope.launchIO {
                releaseNotes.update { DataLoadingState.Loading }
                try {
                    val release = updateChecker.getRelease(updateChecker.getInstalledVersion())
                    if (release != null) {
                        releaseNotes.update { DataLoadingState.Success(release) }
                    } else {
                        releaseNotes.update { DataLoadingState.Error("Release not found") }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching release")
                    releaseNotes.update { DataLoadingState.Error(ex) }
                }
            }
        }

        companion object {
            suspend fun resetSubtitleSettings(appPreferences: DataStore<AppPreferences>) {
                appPreferences.updateData {
                    it.updateSubtitlePreferences {
                        resetSubtitles()
                    }
                }
            }
        }
    }

data class ExternalPlayerApp(
    val name: String,
    val icon: ImageBitmap?,
    val identifier: String,
)

fun getExternalPlayers(context: Context): List<ExternalPlayerApp> {
    val fakeIntent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType("https://example.com/video.mp4".toUri(), "video/*")
        }
    val externalPlayers =
        context.packageManager
            .queryIntentActivities(fakeIntent, PackageManager.MATCH_ALL)
            .filter { it.priority >= 0 }
            .map {
                val component =
                    ComponentName(
                        it.activityInfo.packageName,
                        it.activityInfo.name,
                    )
                ExternalPlayerApp(
                    name = it.loadLabel(context.packageManager).toString(),
                    icon =
                        it
                            .loadIcon(context.packageManager)
                            .toBitmap()
                            .asImageBitmap(),
                    identifier = component.flattenToString(),
                )
            }
    return externalPlayers
}
