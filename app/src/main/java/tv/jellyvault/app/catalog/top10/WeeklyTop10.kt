package tv.jellyvault.app.catalog.top10

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import tv.jellyvault.app.services.hilt.StandardOkHttpClient
import tv.jellyvault.app.util.JvDispatchers
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class Top10(
    val week: LocalDate,
    val titles: List<String>,
)

/**
 * Latest week of [country]/[category] in the weekly Top 10 TSV, titles in rank order, each show once
 */
fun parseWeeklyTop10(
    lines: Sequence<String>,
    country: String = "FR",
    category: String = "TV",
): Top10? {
    var week: LocalDate? = null
    var ranked = mutableListOf<Pair<Int, String>>()
    for (line in lines.drop(1)) {
        val f = line.split('\t')
        if (f.size < 6 || f[1] != country || f[3] != category) continue
        val w = runCatching { LocalDate.parse(f[2]) }.getOrNull() ?: continue
        val rank = f[4].toIntOrNull() ?: continue
        val current = week
        if (current == null || w.isAfter(current)) {
            week = w
            ranked = mutableListOf(rank to f[5])
        } else if (w == current) {
            ranked += rank to f[5]
        }
    }
    val latest = week ?: return null
    return Top10(latest, ranked.sortedBy { it.first }.map { it.second }.distinct())
}

/**
 * Download again only when the cached week is more than 7 days old, and at most once a day
 */
fun needsRefresh(
    cachedWeek: LocalDate?,
    fetchedAtMillis: Long?,
    today: LocalDate,
    nowMillis: Long,
): Boolean {
    // At most one attempt a day, successful or not (the file is 32 MB)
    if (fetchedAtMillis != null && nowMillis - fetchedAtMillis < TimeUnit.DAYS.toMillis(1)) return false
    if (cachedWeek == null) return true
    return ChronoUnit.DAYS.between(cachedWeek, today) > 7
}

@Serializable
private data class CachedTop10(
    val week: String,
    val titles: List<String>,
    val fetchedAt: Long,
)

/**
 * Tiny JSON cache of the last Top 10
 */
class WeeklyTop10Cache(
    private val file: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun read(): CachedTop10? = runCatching { json.decodeFromString(CachedTop10.serializer(), file.readText()) }.getOrNull()

    fun write(
        top: Top10,
        fetchedAt: Long,
    ) = write(CachedTop10(top.week.toString(), top.titles, fetchedAt))

    private fun write(cached: CachedTop10) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(CachedTop10.serializer(), cached))
        tmp.renameTo(file)
    }

    /** Remember a failed or empty attempt so the next one waits a day, keeping the titles we had */
    private fun rememberAttempt(
        cached: CachedTop10?,
        nowMillis: Long,
    ): List<String> {
        write((cached ?: CachedTop10(week = "", titles = emptyList(), fetchedAt = 0L)).copy(fetchedAt = nowMillis))
        return cached?.titles.orEmpty()
    }

    /**
     * The cached titles, downloading first when [needsRefresh]; a failed download keeps the cache
     */
    suspend fun refreshOrKeep(
        today: LocalDate,
        nowMillis: Long,
        download: suspend () -> Top10?,
    ): List<String> {
        val cached = read()
        val cachedWeek = cached?.let { runCatching { LocalDate.parse(it.week) }.getOrNull() }
        if (!needsRefresh(cachedWeek, cached?.fetchedAt, today, nowMillis)) return cached?.titles.orEmpty()
        return try {
            val fresh = download()
            if (fresh != null) {
                write(fresh, nowMillis)
                fresh.titles
            } else {
                rememberAttempt(cached, nowMillis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Weekly Top 10 download failed, keeping the cache")
            rememberAttempt(cached, nowMillis)
        }
    }
}

/**
 * France weekly Top 10 series, from the official weekly data (about 32 MB, streamed line by line, at most weekly)
 */
@Singleton
class WeeklyTop10
    @Inject
    constructor(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
        @ApplicationContext context: Context,
    ) {
        private val client = okHttpClient.newBuilder().callTimeout(2, TimeUnit.MINUTES).build()
        private val cache = WeeklyTop10Cache(File(context.filesDir, "weekly_top10.json"))

        internal var now: () -> Long = System::currentTimeMillis

        /** One download at a time, even when the Séries page opens again meanwhile */
        private val mutex = Mutex()

        suspend fun titles(): List<String> =
            mutex.withLock {
                load()
            }

        private suspend fun load(): List<String> =
            withContext(JvDispatchers.IO) {
                cache.refreshOrKeep(LocalDate.now(), now()) {
                    val request = Request.Builder().url(URL).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Weekly Top 10 HTTP ${response.code}")
                        response.body
                            .byteStream()
                            .bufferedReader()
                            .useLines { parseWeeklyTop10(it) }
                    }
                }
            }

        companion object {
            const val URL = "https://www.netflix.com/tudum/top10/data/all-weeks-countries.tsv"
        }
    }
