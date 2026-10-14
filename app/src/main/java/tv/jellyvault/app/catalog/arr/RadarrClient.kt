package tv.jellyvault.app.catalog.arr

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadarrClient
    @Inject
    constructor(
        private val http: ArrHttp,
        private val settings: ArrSettingsRepository,
    ) {
        private suspend fun config(override: ArrConfig?) = override ?: settings.current(ArrService.RADARR)

        suspend fun systemStatus(config: ArrConfig? = null): SystemStatus = http.decode(http.get(config(config), "system/status"))

        suspend fun qualityProfiles(config: ArrConfig? = null): List<QualityProfile> =
            http.decode(http.get(config(config), "qualityprofile"))

        suspend fun rootFolders(config: ArrConfig? = null): List<RootFolder> = http.decode(http.get(config(config), "rootfolder"))

        suspend fun queue(): List<QueueRecord> =
            http
                .decode<QueuePage>(
                    http.get(config(null), "queue", mapOf("pageSize" to QUEUE_PAGE_SIZE, "includeMovie" to "true")),
                ).records

        suspend fun movieByTmdbId(tmdbId: Int): RadarrMovie? =
            http.decode<List<RadarrMovie>>(http.get(config(null), "movie", mapOf("tmdbId" to tmdbId.toString()))).firstOrNull()

        /**
         * Add the movie using the saved quality profile and root folder, and start searching for it
         */
        suspend fun addMovie(tmdbId: Int): RadarrMovie {
            val config = config(null)
            if (!config.isConfigured) throw ArrException.NotConfigured()
            val lookup: JsonObject = http.decode(http.get(config, "movie/lookup/tmdb", mapOf("tmdbId" to tmdbId.toString())))
            val body =
                JsonObject(
                    lookup +
                        mapOf(
                            "qualityProfileId" to JsonPrimitive(config.qualityProfileId),
                            "rootFolderPath" to JsonPrimitive(config.rootFolderPath),
                            "monitored" to JsonPrimitive(true),
                            "minimumAvailability" to JsonPrimitive("released"),
                            "addOptions" to buildJsonObject { put("searchForMovie", true) },
                        ),
                )
            return http.decode(http.send(config, "POST", "movie", body.toString()))
        }

        companion object {
            const val QUEUE_PAGE_SIZE = "200"
        }
    }
