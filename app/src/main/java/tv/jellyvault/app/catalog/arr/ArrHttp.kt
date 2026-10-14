package tv.jellyvault.app.catalog.arr

import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import tv.jellyvault.app.services.hilt.StandardOkHttpClient
import tv.jellyvault.app.util.JvDispatchers
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ArrException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class NotConfigured : ArrException("Not configured")

    /**
     * @param basicAuth true when the reverse proxy (HTTP Basic) refused access, false when the API key was refused
     */
    class Unauthorized(
        val code: Int,
        val basicAuth: Boolean = false,
    ) : ArrException("Unauthorized ($code)")

    class InvalidSettings(
        cause: Throwable? = null,
    ) : ArrException("Invalid URL or credentials", cause)

    class Unreachable(
        cause: Throwable,
    ) : ArrException("Unreachable", cause)

    class Http(
        val code: Int,
    ) : ArrException("HTTP $code")

    class InvalidResponse(
        cause: Throwable,
    ) : ArrException("Invalid response", cause)
}

/**
 * Shared HTTP layer for Radarr/Sonarr v3 APIs: base URL (with sub path), X-Api-Key, optional basic auth, typed errors
 */
@Singleton
class ArrHttp
    @Inject
    constructor(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
    ) {
        private val client = okHttpClient.newBuilder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

        val json = Json { ignoreUnknownKeys = true }

        suspend fun get(
            config: ArrConfig,
            path: String,
            query: Map<String, String> = emptyMap(),
        ): String = execute(config, "GET", path, query, null)

        suspend fun send(
            config: ArrConfig,
            method: String,
            path: String,
            body: String,
        ): String = execute(config, method, path, emptyMap(), body)

        private suspend fun execute(
            rawConfig: ArrConfig,
            method: String,
            path: String,
            query: Map<String, String>,
            body: String?,
        ): String {
            // Values typed with a TV keyboard or pasted can carry newlines or zero-width characters
            val config =
                rawConfig.copy(
                    url = rawConfig.url.withoutInvisible().trim(),
                    apiKey = rawConfig.apiKey.withoutInvisible().trim(),
                    basicUser = rawConfig.basicUser.withoutInvisible().trim(),
                    basicPassword = rawConfig.basicPassword.withoutInvisible(),
                )
            if (!config.canConnect) throw ArrException.NotConfigured()
            val url = buildUrl(config.url, path, query) ?: throw ArrException.InvalidSettings()
            val request =
                try {
                    Request
                        .Builder()
                        .url(url)
                        .header("X-Api-Key", config.apiKey)
                        .header("Accept", "application/json")
                        .apply {
                            if (config.basicUser.isNotBlank()) {
                                header("Authorization", Credentials.basic(config.basicUser, config.basicPassword))
                            }
                        }.method(method, body?.toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                } catch (e: IllegalArgumentException) {
                    Timber.w(e, "Invalid Radarr/Sonarr settings")
                    throw ArrException.InvalidSettings(e)
                }
            return withContext(JvDispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        when {
                            response.code == 401 || response.code == 403 -> {
                                val basic = response.header("WWW-Authenticate")?.startsWith("Basic", ignoreCase = true) == true
                                throw ArrException.Unauthorized(response.code, basicAuth = basic)
                            }

                            !response.isSuccessful -> {
                                throw ArrException.Http(response.code)
                            }

                            else -> {
                                response.body.string()
                            }
                        }
                    }
                } catch (e: IOException) {
                    throw ArrException.Unreachable(e)
                }
            }
        }

        companion object {
            const val TIMEOUT_SECONDS = 10L
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()

            fun buildUrl(
                base: String,
                path: String,
                query: Map<String, String>,
            ): HttpUrl? =
                base
                    .trim()
                    .trimEnd('/')
                    .toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addPathSegments("api/v3/${path.trimStart('/')}")
                    ?.apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
                    ?.build()
        }
    }

/**
 * Decode [text] with [ArrHttp.json], converting serialization failures to [ArrException.InvalidResponse]
 */
internal inline fun <reified T> ArrHttp.decode(text: String): T =
    try {
        json.decodeFromString<T>(text)
    } catch (e: IllegalArgumentException) {
        throw ArrException.InvalidResponse(e)
    }

private val INVISIBLE = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')

/**
 * Drop control characters (newlines, tabs) and zero-width characters
 */
internal fun String.withoutInvisible(): String = filterNot { it.isISOControl() || it in INVISIBLE }
