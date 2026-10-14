package tv.jellyvault.app.catalog.tmdb

import java.time.LocalDate

enum class TmdbSort(
    val param: String,
) {
    POPULARITY("popularity.desc"),
    RATING("vote_average.desc"),
    REVENUE("revenue.desc"),
}

/**
 * One TMDB discover request: a row, a grid, a genre, a studio or a network
 */
data class TmdbDiscoverQuery(
    val mediaType: TmdbMediaType,
    val genreId: Int? = null,
    val companyId: Int? = null,
    val networkId: Int? = null,
    val sort: TmdbSort = TmdbSort.POPULARITY,
    val minVotes: Int = 200,
    val maxVotes: Int? = null,
    val minRating: Double? = null,
    val releasedAfter: LocalDate? = null,
    val releasedBefore: LocalDate? = null,
    /** Any of these studios ("or"); ignored when [companyId] is set */
    val companyIds: List<Int> = emptyList(),
    /** Any of these networks ("or"); ignored when [networkId] is set */
    val networkIds: List<Int> = emptyList(),
    /** Series with an episode aired since this date */
    val airedAfter: LocalDate? = null,
    /** Drop obscure foreign-language titles (see [isMainstream]); for "popular" style rows */
    val mainstreamOnly: Boolean = false,
    /** Genres that must ALL match besides [genreId] ("and"), e.g. comedy + romance */
    val andGenreIds: List<Int> = emptyList(),
    /** TMDB keywords, any of them ("or") */
    val keywordIds: List<Int> = emptyList(),
    /** ISO 3166-1 country the title comes from, e.g. "FR" */
    val originCountry: String? = null,
    /** Any of these genres ("or"); used when [genreId] is null */
    val anyGenreIds: List<Int> = emptyList(),
    /** TMDB series type, e.g. 2 for mini-series */
    val tvType: Int? = null,
) {
    fun params(page: Int): Map<String, String> =
        buildMap {
            genreId?.let { put("with_genres", (listOf(it) + andGenreIds).joinToString(",")) }
                ?: anyGenreIds.takeIf { it.isNotEmpty() }?.let { put("with_genres", it.joinToString("|")) }
            tvType?.let { put("with_type", it.toString()) }
            keywordIds.takeIf { it.isNotEmpty() }?.let { put("with_keywords", it.joinToString("|")) }
            originCountry?.let { put("with_origin_country", it) }
            (companyId?.toString() ?: companyIds.takeIf { it.isNotEmpty() }?.joinToString("|"))?.let { put("with_companies", it) }
            (networkId?.toString() ?: networkIds.takeIf { it.isNotEmpty() }?.joinToString("|"))?.let { put("with_networks", it) }
            airedAfter?.let { put("air_date.gte", it.toString()) }
            put("sort_by", sort.param)
            put("vote_count.gte", minVotes.toString())
            maxVotes?.let { put("vote_count.lte", it.toString()) }
            minRating?.let { put("vote_average.gte", it.toString()) }
            val dateField = if (mediaType == TmdbMediaType.MOVIE) "primary_release_date" else "first_air_date"
            releasedAfter?.let { put("$dateField.gte", it.toString()) }
            releasedBefore?.let { put("$dateField.lte", it.toString()) }
            put("include_adult", "false")
            put("page", page.toString())
        }
}
