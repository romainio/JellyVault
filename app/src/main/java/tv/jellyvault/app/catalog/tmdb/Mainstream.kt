package tv.jellyvault.app.catalog.tmdb

/** Languages a viewer expects in "popular" rows, with the interface language */
private val HOME_LANGUAGES = setOf("fr", "en")

private const val ANIMATION_GENRE_ID = 16

/** A foreign-language title needs this many votes... */
internal const val INTERNATIONAL_MIN_VOTES = 3000

/** ...or this TMDB popularity (a fresh hit that has not had time to collect votes) */
internal const val INTERNATIONAL_MIN_POPULARITY = 250.0

/**
 * Whether a title belongs in a "trending / popular" row: French and English titles, animation,
 * and foreign-language titles only when they are a real success. Keeps obscure Korean or Spanish
 * films out of the trending rows without hiding Squid Game.
 */
fun TmdbItem.isMainstream(): Boolean =
    originalLanguage == null ||
        originalLanguage in HOME_LANGUAGES ||
        originalLanguage == TmdbLanguage.current().code ||
        ANIMATION_GENRE_ID in genreIds ||
        voteCount >= INTERNATIONAL_MIN_VOTES ||
        popularity >= INTERNATIONAL_MIN_POPULARITY

fun List<TmdbItem>.mainstream(): List<TmdbItem> = filter { it.isMainstream() }
