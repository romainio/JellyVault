package tv.jellyvault.app.catalog.tmdb

import java.util.Locale

/**
 * The language TMDB texts (titles, overviews, genres, logos, trailers) are asked in: the app's interface language
 */
data class TmdbLanguage(
    /** ISO 639-1 code, e.g. "en" */
    val code: String,
    /** TMDB `language` parameter, e.g. "en-US" */
    val tag: String,
) {
    /** Languages of the trailers and logos TMDB returns, preferred first ("null" for logos without text) */
    val mediaLanguages: List<String> get() = listOf(code, "en").distinct()

    companion object {
        /**
         * The country TMDB is asked for with each language, so "fr" keeps the France translations whatever the TV's region.
         * Other languages use the locale's own country.
         */
        private val MAIN_COUNTRY =
            mapOf("fr" to "FR", "en" to "US", "de" to "DE", "es" to "ES", "it" to "IT", "nl" to "NL")

        /** Java still reports a few renamed ISO 639 codes under their old name; TMDB uses the current one */
        private val CURRENT_CODE = mapOf("iw" to "he", "in" to "id", "ji" to "yi")

        /**
         * The language picked in Settings › User interface language for the signed-in profile, null for the system one.
         * Set by the server repository, so it is known as soon as the session is restored, before any row loads.
         */
        @Volatile
        var profileLanguageTag: () -> String? = { null }

        @Volatile
        private var last: Pair<Locale, TmdbLanguage>? = null

        fun of(locale: Locale): TmdbLanguage {
            val code = locale.language.ifBlank { "en" }.let { CURRENT_CODE[it] ?: it }
            val country = MAIN_COUNTRY[code] ?: locale.country
            return TmdbLanguage(code, if (country.isBlank()) code else "$code-$country")
        }

        /**
         * The interface language: the profile's choice, else the system language. Cheap enough to call per card.
         */
        fun current(): TmdbLanguage {
            val locale = profileLanguageTag()?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: Locale.getDefault()
            last?.let { (cachedLocale, language) -> if (cachedLocale == locale) return language }
            return of(locale).also { last = locale to it }
        }
    }
}
