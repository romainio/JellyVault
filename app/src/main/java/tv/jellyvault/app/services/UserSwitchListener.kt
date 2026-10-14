package tv.jellyvault.app.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.JellyfinUser
import tv.jellyvault.app.ui.launchDefault
import tv.jellyvault.app.ui.launchIO
import tv.jellyvault.app.util.JvDispatchers
import javax.inject.Inject

/**
 * Listens for JF user switching in the app to also switch other settings like locale and home settings
 */
@ActivityScoped
class UserSwitchListener
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val homeSettingsService: HomeSettingsService,
    ) {
        init {
            context as AppCompatActivity
            context.lifecycleScope.launchDefault {
                serverRepository.currentUserFlow.collect { user ->
                    Timber.d("New user")
                    homeSettingsService.currentSettings.update { HomePageResolvedSettings.EMPTY }
                    if (user != null) {
                        switchUser(user)
                    }
                }
            }
        }

        private suspend fun switchUser(user: JellyfinUser) =
            supervisorScope {
                // Switch the locale to either the user's choice or the system default (empty)
                val localeList =
                    user.uiLanguage?.let { LocaleListCompat.forLanguageTags(it) }
                        ?: LocaleListCompat.getEmptyLocaleList()
                Timber.i("Switching locale to %s", localeList)
                withContext(JvDispatchers.Main) {
                    AppCompatDelegate.setApplicationLocales(localeList)
                }

                // Check for home settings
                launchIO {
                    homeSettingsService.loadCurrentSettings(user.id)
                }
            }
    }
