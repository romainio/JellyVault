package tv.jellyvault.app.catalog.tmdb

import okhttp3.HttpUrl
import okhttp3.Request
import java.io.File

/**
 * A TMDB credential as the user pastes it: a v3 API key (32 hex characters) or a v4 read access token
 */
sealed interface TmdbAuth {
    data class ApiKey(
        val key: String,
    ) : TmdbAuth

    data class Bearer(
        val token: String,
    ) : TmdbAuth

    companion object {
        private val API_KEY = Regex("^[0-9a-fA-F]{32}$")

        /** A real key or token is printable ASCII without spaces: anything else was mistyped or mangled by the keyboard */
        private fun CharSequence.isHeaderSafe() = all { it in '\u0021'..'\u007E' }

        /** Null when [credential] is blank or malformed (inner whitespace, smart quotes...): treated as not configured */
        fun of(credential: String): TmdbAuth? {
            val value = credential.trim()
            return when {
                value.isEmpty() || !value.isHeaderSafe() -> null
                API_KEY.matches(value) -> ApiKey(value)
                else -> Bearer(value)
            }
        }
    }
}

/** The TMDB credential to use now, read on every request so a newly entered key applies at once */
fun interface TmdbCredential {
    fun current(): String
}

/**
 * [url] signed with [credential]: a v3 key as the api_key parameter, a v4 token as a bearer header
 */
fun Request.Builder.tmdbRequest(
    url: HttpUrl,
    credential: String,
): Request.Builder =
    when (val auth = TmdbAuth.of(credential)) {
        is TmdbAuth.ApiKey -> url(url.newBuilder().addQueryParameter("api_key", auth.key).build())
        is TmdbAuth.Bearer -> url(url).header("Authorization", "Bearer ${auth.token}")
        null -> url(url)
    }

/**
 * The key entered in the app, kept in no-backup storage like the Radarr/Sonarr keys; [buildDefault] (local.properties
 * `tmdb.readToken`) when none was entered
 */
class TmdbKeyStore(
    private val file: File,
    private val buildDefault: String,
) : TmdbCredential {
    @Volatile
    private var userKey: String? = null

    override fun current(): String = userKey().ifBlank { buildDefault }

    val usesBuildDefault: Boolean get() = userKey().isBlank() && buildDefault.isNotBlank()

    @Synchronized
    fun userKey(): String = userKey ?: (if (file.exists()) file.readText().trim() else "").also { userKey = it }

    @Synchronized
    fun save(value: String) {
        val key = value.trim()
        if (key.isEmpty()) {
            file.delete()
        } else {
            file.parentFile?.mkdirs()
            file.writeText(key)
        }
        userKey = key
    }
}
