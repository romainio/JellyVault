package tv.jellyvault.app.catalog.arr

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SonarrClient
    @Inject
    constructor(
        private val http: ArrHttp,
        private val settings: ArrSettingsRepository,
    ) {
        private suspend fun config(override: ArrConfig?) = override ?: settings.current(ArrService.SONARR)

        suspend fun systemStatus(config: ArrConfig? = null): SystemStatus = http.decode(http.get(config(config), "system/status"))

        suspend fun qualityProfiles(config: ArrConfig? = null): List<QualityProfile> =
            http.decode(http.get(config(config), "qualityprofile"))

        suspend fun rootFolders(config: ArrConfig? = null): List<RootFolder> = http.decode(http.get(config(config), "rootfolder"))

        suspend fun queue(): List<QueueRecord> =
            http
                .decode<QueuePage>(
                    http.get(config(null), "queue", mapOf("pageSize" to RadarrClient.QUEUE_PAGE_SIZE, "includeSeries" to "true")),
                ).records

        suspend fun seriesByTvdbId(tvdbId: Int): SonarrSeries? =
            http.decode<List<SonarrSeries>>(http.get(config(null), "series", mapOf("tvdbId" to tvdbId.toString()))).firstOrNull()

        /**
         * Seasons of a series as Sonarr (TVDB) numbers them, before it is added
         */
        suspend fun lookupSeasons(tvdbId: Int): List<SonarrSeason> =
            http
                .decode<List<SonarrLookup>>(http.get(config(null), "series/lookup", mapOf("term" to "tvdb:$tvdbId")))
                .firstOrNull()
                ?.seasons
                .orEmpty()

        /**
         * Add the series monitoring only [seasons]. No addOptions.monitor: Sonarr then follows seasons[].monitored.
         */
        suspend fun addSeries(
            tvdbId: Int,
            seasons: Set<Int>,
        ): SonarrSeries {
            val config = config(null)
            if (!config.isConfigured) throw ArrException.NotConfigured()
            val lookup =
                http.decode<List<JsonObject>>(http.get(config, "series/lookup", mapOf("term" to "tvdb:$tvdbId"))).firstOrNull()
                    ?: throw ArrException.Http(404)
            val body =
                JsonObject(
                    lookup +
                        mapOf(
                            "seasons" to withMonitoredSeasons(lookup, seasons, keepExisting = false),
                            "qualityProfileId" to JsonPrimitive(config.qualityProfileId),
                            "rootFolderPath" to JsonPrimitive(config.rootFolderPath),
                            "monitored" to JsonPrimitive(true),
                            "seasonFolder" to JsonPrimitive(true),
                            "addOptions" to buildJsonObject { put("searchForMissingEpisodes", true) },
                        ),
                )
            return http.decode(http.send(config, "POST", "series", body.toString()))
        }

        /**
         * Also monitor [seasons] on an existing series (keeping already monitored ones) and search for them
         */
        suspend fun monitorSeasons(
            seriesId: Int,
            seasons: Set<Int>,
        ) {
            val config = config(null)
            val series: JsonObject = http.decode(http.get(config, "series/$seriesId"))
            val updated = JsonObject(series + ("seasons" to withMonitoredSeasons(series, seasons, keepExisting = true)))
            http.send(config, "PUT", "series/$seriesId", updated.toString())
            seasons.sorted().forEach { season ->
                val command =
                    buildJsonObject {
                        put("name", "SeasonSearch")
                        put("seriesId", seriesId)
                        put("seasonNumber", season)
                    }
                http.send(config, "POST", "command", command.toString())
            }
        }

        private fun withMonitoredSeasons(
            series: JsonObject,
            seasons: Set<Int>,
            keepExisting: Boolean,
        ): JsonArray =
            JsonArray(
                series["seasons"]?.jsonArray.orEmpty().map { element ->
                    val season = element.jsonObject
                    val number = season["seasonNumber"]!!.jsonPrimitive.int
                    val wasMonitored = season["monitored"]?.jsonPrimitive?.content == "true"
                    JsonObject(season + ("monitored" to JsonPrimitive(number in seasons || (keepExisting && wasMonitored))))
                },
            )
    }
