package tv.jellyvault.app.catalog.account

import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.jellyvault.app.catalog.tmdb.TmdbAuth
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbCredential
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbLanguage
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbRawItem
import tv.jellyvault.app.catalog.tmdb.tmdbRequest
import tv.jellyvault.app.services.hilt.StandardOkHttpClient
import tv.jellyvault.app.util.JvDispatchers
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class TmdbAccountException(
    message: String,
    val code: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** The session was refused or has expired: the account must be linked again */
    val unauthorized: Boolean get() = code == 401
}

data class TmdbAccountInfo(
    val id: Int,
    val username: String?,
)

/**
 * A title the user rated (1 to 10, IMDb ratings copied to TMDB by the sync)
 */
data class RatedTitle(
    val item: TmdbItem,
    val rating: Double,
)

@Serializable
private data class RawRequestToken(
    @SerialName("request_token") val requestToken: String? = null,
)

@Serializable
private data class RawSession(
    @SerialName("session_id") val sessionId: String? = null,
)

@Serializable
private data class RawAccount(
    val id: Int,
    val username: String? = null,
)

@Serializable
private data class RawAccountPage(
    val results: List<JsonObject> = emptyList(),
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
)

/**
 * The user's TMDB account (v3 sessions): linking, ratings and watchlist
 */
@Singleton
class TmdbAccountClient
    @Inject
    constructor(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
        private val credential: TmdbCredential,
    ) {
        private val client = okHttpClient.newBuilder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
        private val json = Json { ignoreUnknownKeys = true }

        /** Read at each request: the interface language can change while the app runs */
        internal var language: () -> TmdbLanguage = TmdbLanguage::current

        val isConfigured: Boolean get() = TmdbAuth.of(credential.current()) != null

        suspend fun createRequestToken(): String =
            get<RawRequestToken>(url("authentication/token/new")).requestToken
                ?: throw TmdbAccountException("TMDB gave no request token")

        /**
         * After the user approved [requestToken] on the TMDB website
         */
        suspend fun createSession(requestToken: String): String {
            val body = """{"request_token":"$requestToken"}""".toRequestBody(JSON_TYPE)
            return send<RawSession>(Request.Builder().post(body), url("authentication/session/new"), "authentication/session/new").sessionId
                ?: throw TmdbAccountException("TMDB gave no session", 401)
        }

        suspend fun account(sessionId: String): TmdbAccountInfo =
            get<RawAccount>(url("account", "session_id" to sessionId)).let { TmdbAccountInfo(it.id, it.username) }

        /**
         * Every rated title of [mediaType], most recently rated first
         */
        suspend fun rated(
            mediaType: TmdbMediaType,
            accountId: Int,
            sessionId: String,
        ): List<RatedTitle> =
            pages("account/$accountId/rated/${mediaType.plural}", sessionId, MAX_RATING_PAGES) { obj ->
                val item = json.decodeFromJsonElement<TmdbRawItem>(obj).toItem(mediaType) ?: return@pages null
                val rating = obj["rating"]?.jsonPrimitive?.doubleOrNull ?: return@pages null
                RatedTitle(item, rating)
            }

        /**
         * Rate a title from 0.5 to 10, or remove the rating when [value] is null
         */
        suspend fun rate(
            mediaType: TmdbMediaType,
            tmdbId: Int,
            sessionId: String,
            value: Double?,
        ) {
            val url = url("${mediaType.path}/$tmdbId/rating", "session_id" to sessionId)
            val builder =
                if (value == null) {
                    Request.Builder().delete()
                } else {
                    Request.Builder().post("""{"value":$value}""".toRequestBody(JSON_TYPE))
                }
            send<JsonObject>(builder, url, url.encodedPath)
        }

        /**
         * The watchlist of [mediaType], most recently added first
         */
        suspend fun watchlist(
            mediaType: TmdbMediaType,
            accountId: Int,
            sessionId: String,
        ): List<TmdbItem> =
            pages("account/$accountId/watchlist/${mediaType.plural}", sessionId, MAX_WATCHLIST_PAGES) { obj ->
                json.decodeFromJsonElement<TmdbRawItem>(obj).toItem(mediaType)
            }

        private suspend fun <T : Any> pages(
            path: String,
            sessionId: String,
            maxPages: Int,
            convert: (JsonObject) -> T?,
        ): List<T> {
            val all = mutableListOf<T>()
            var page = 1
            while (page <= maxPages) {
                val raw =
                    get<RawAccountPage>(url(path, "session_id" to sessionId, "sort_by" to "created_at.desc", "page" to page.toString()))
                raw.results.mapNotNullTo(all) {
                    try {
                        convert(it)
                    } catch (e: SerializationException) {
                        null
                    }
                }
                if (page >= raw.totalPages) break
                page++
            }
            return all
        }

        private fun url(
            path: String,
            vararg params: Pair<String, String>,
        ): HttpUrl {
            if (!isConfigured) throw TmdbAccountException("TMDB read token is not configured")
            return TmdbClient.BASE_URL
                .toHttpUrl()
                .newBuilder()
                .addPathSegments(path)
                .addQueryParameter("language", language().tag)
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
        }

        private suspend inline fun <reified T> get(url: HttpUrl): T = send(Request.Builder().get(), url, url.encodedPath)

        private suspend inline fun <reified T> send(
            builder: Request.Builder,
            url: HttpUrl,
            path: String,
        ): T =
            withContext(JvDispatchers.IO) {
                try {
                    val request =
                        builder
                            .tmdbRequest(url, credential.current())
                            .header("Accept", "application/json")
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw TmdbAccountException("TMDB HTTP ${response.code} for $path", response.code)
                        json.decodeFromString<T>(response.body.string())
                    }
                } catch (e: IOException) {
                    throw TmdbAccountException("TMDB unreachable", cause = e)
                } catch (e: SerializationException) {
                    throw TmdbAccountException("Invalid TMDB response for $path", cause = e)
                } catch (e: IllegalArgumentException) {
                    // Last, as SerializationException is one too: a credential OkHttp refuses in a header; its message quotes the key, so no cause
                    throw TmdbAccountException("TMDB credential is invalid")
                }
            }

        companion object {
            const val TIMEOUT_SECONDS = 15L
            const val MAX_RATING_PAGES = 50
            const val MAX_WATCHLIST_PAGES = 10
            private val JSON_TYPE = "application/json".toMediaType()

            fun approvalUrl(requestToken: String) = "https://www.themoviedb.org/authenticate/$requestToken"

            private val TmdbMediaType.plural: String get() = if (this == TmdbMediaType.MOVIE) "movies" else "tv"
        }
    }
