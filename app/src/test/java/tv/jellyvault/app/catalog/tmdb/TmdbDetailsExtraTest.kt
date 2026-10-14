package tv.jellyvault.app.catalog.tmdb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbDetailsExtraTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun movie_extras_are_read() {
        val raw =
            json.decodeFromString<TmdbRawDetails>(
                """
                {"id":1,"title":"Toy Story","poster_path":"/p.jpg","release_date":"1995-11-22","budget":30000000,
                 "belongs_to_collection":{"id":10194,"name":"Toy Story - Saga"},
                 "production_companies":[{"id":3,"name":"Pixar"},{"id":2,"name":"Walt Disney Pictures"}],
                 "production_countries":[{"iso_3166_1":"US","name":"États-Unis d'Amérique"}],
                 "credits":{"cast":[{"name":"Tom Hanks","character":"Woody","profile_path":"/t.jpg","order":0}],
                            "crew":[{"name":"John Lasseter","job":"Director"},{"name":"X","job":"Writer"}]},
                 "similar":{"results":[{"id":2,"title":"A","poster_path":"/a.jpg"}]}}
                """.trimIndent(),
            )
        val d = raw.toDetails(TmdbMediaType.MOVIE, FRENCH)!!
        assertEquals(TmdbNamed(10194, "Toy Story - Saga"), d.collection)
        assertEquals(TmdbNamed(3, "Pixar"), d.studio)
        assertEquals(listOf("John Lasseter"), d.makers)
        assertEquals(listOf("États-Unis d'Amérique"), d.countries)
        assertEquals(30000000L, d.budget)
        assertEquals("Tom Hanks", d.cast.single().name)
        assertEquals("https://image.tmdb.org/t/p/w185/t.jpg", d.cast.single().profileUrl)
        assertEquals(listOf(2), d.similarTitles.map { it.tmdbId })
        assertEquals("1995-11-22", d.releaseDate)
    }

    @Test
    fun series_use_network_creators_and_episodes() {
        val raw =
            json.decodeFromString<TmdbRawDetails>(
                """
                {"id":5,"name":"The Last of Us","poster_path":"/p.jpg","first_air_date":"2023-01-15","number_of_episodes":16,
                 "networks":[{"id":49,"name":"HBO"}],"created_by":[{"name":"Craig Mazin"},{"name":"Neil Druckmann"}]}
                """.trimIndent(),
            )
        val d = raw.toDetails(TmdbMediaType.TV, FRENCH)!!
        assertEquals(TmdbNamed(49, "HBO"), d.network)
        assertEquals(listOf("Craig Mazin", "Neil Druckmann"), d.makers)
        assertEquals(16, d.episodeCount)
        assertEquals("2023-01-15", d.releaseDate)
    }

    @Test
    fun missing_fields_give_nulls_and_empty_lists() {
        val d =
            json
                .decodeFromString<TmdbRawDetails>(
                    """{"id":1,"title":"A","poster_path":"/p.jpg","budget":0}""",
                ).toDetails(TmdbMediaType.MOVIE, FRENCH)!!
        assertNull(d.collection)
        assertNull(d.studio)
        assertNull(d.budget)
        assertTrue(d.cast.isEmpty() && d.makers.isEmpty() && d.similarTitles.isEmpty())
    }
}
