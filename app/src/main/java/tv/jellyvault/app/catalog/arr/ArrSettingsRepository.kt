package tv.jellyvault.app.catalog.arr

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ArrConfig(
    val url: String = "",
    val apiKey: String = "",
    val basicUser: String = "",
    val basicPassword: String = "",
    val qualityProfileId: Int = 0,
    val rootFolderPath: String = "",
) {
    val canConnect: Boolean get() = url.isNotBlank() && apiKey.isNotBlank()
    val isConfigured: Boolean get() = canConnect && qualityProfileId > 0 && rootFolderPath.isNotBlank()
}

/**
 * Radarr/Sonarr connection settings, stored on the device only (excluded from Android backups)
 */
@Singleton
class ArrSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<ArrSettings>,
    ) {
        fun config(service: ArrService): Flow<ArrConfig> = dataStore.data.map { it.forService(service).toConfig() }

        suspend fun current(service: ArrService): ArrConfig = config(service).first()

        suspend fun update(
            service: ArrService,
            transform: (ArrConfig) -> ArrConfig,
        ) {
            dataStore.updateData { settings ->
                val updated = transform(settings.forService(service).toConfig()).toProto()
                when (service) {
                    ArrService.RADARR -> settings.toBuilder().setRadarr(updated).build()
                    ArrService.SONARR -> settings.toBuilder().setSonarr(updated).build()
                }
            }
        }

        private fun ArrSettings.forService(service: ArrService) =
            when (service) {
                ArrService.RADARR -> radarr
                ArrService.SONARR -> sonarr
            }

        private fun ArrServiceSettings.toConfig() = ArrConfig(url, apiKey, basicUser, basicPassword, qualityProfileId, rootFolderPath)

        private fun ArrConfig.toProto(): ArrServiceSettings =
            ArrServiceSettings
                .newBuilder()
                .setUrl(url.trim())
                .setApiKey(apiKey.trim())
                .setBasicUser(basicUser)
                .setBasicPassword(basicPassword)
                .setQualityProfileId(qualityProfileId)
                .setRootFolderPath(rootFolderPath)
                .build()
    }
