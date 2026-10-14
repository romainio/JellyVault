package tv.jellyvault.app.catalog.tmdb

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.util.JvDispatchers
import tv.jellyvault.app.util.configure
import tv.jellyvault.app.util.reset

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbDetailsTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = JvDispatchers.configure(testDispatcher)

    @After
    fun tearDown() = JvDispatchers.reset()

    private class Fake(
        private val routes: Map<String, String>,
    ) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val body = routes.entries.firstOrNull { request.url.encodedPath.endsWith(it.key) }?.value
            return Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(if (body != null) 200 else 404)
                .message("test")
                .body((body ?: "{}").toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun fixture(name: String) = javaClass.getResource("/tmdb/$name")!!.readText()

    private fun client(fake: Fake) =
        TmdbClient(OkHttpClient.Builder().addInterceptor(fake).build()) { "t" }.also { it.language = { FRENCH } }

    @Test
    fun movie_details_are_parsed() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/movie/27205" to fixture("movie_details.json")))
            val d = client(fake).details(TmdbMediaType.MOVIE, 27205)

            assertEquals("Inception", d.item.title)
            assertEquals(TmdbMediaType.MOVIE, d.item.mediaType)
            assertEquals(148, d.runtimeMinutes)
            assertEquals(listOf("Action", "Science-Fiction"), d.genres)
            assertEquals("Votre esprit est la scène du crime.", d.tagline)
            assertEquals("12", d.certification)
            assertEquals("https://image.tmdb.org/t/p/w500/logo_en.png", d.logoUrl)
            assertEquals("trailerEN", d.trailerKey)
            assertEquals("https://www.youtube.com/watch?v=trailerEN", d.trailerUrl)
            assertEquals(listOf(157336), d.recommendations.map { it.tmdbId })
            assertEquals(TmdbMediaType.MOVIE, d.recommendations[0].mediaType)
            assertNull(d.tvdbId)
            assertEquals(emptyList<TmdbSeason>(), d.seasons)

            val url = fake.requests.single().url
            assertEquals("fr-FR", url.queryParameter("language"))
            assertEquals("fr,en", url.queryParameter("include_video_language"))
            assertEquals("fr,en,null", url.queryParameter("include_image_language"))
            assertEquals(
                "videos,credits,recommendations,similar,images,external_ids,release_dates",
                url.queryParameter("append_to_response"),
            )
        }

    @Test
    fun tv_details_exclude_season_zero_and_sort_and_expose_tvdb() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/tv/1396" to fixture("tv_details.json")))
            val d = client(fake).details(TmdbMediaType.TV, 1396)

            assertEquals("Breaking Bad", d.item.title)
            assertEquals(listOf(1, 2), d.seasons.map { it.number })
            assertEquals(7, d.seasons[0].episodeCount)
            assertEquals(81189, d.tvdbId)
            assertEquals("TV-MA", d.certification)
            assertEquals("https://image.tmdb.org/t/p/w500/bb_fr.png", d.logoUrl)
            assertNull(d.trailerKey)
            assertNull(d.tagline)
            assertEquals(
                "videos,credits,recommendations,similar,images,external_ids,content_ratings",
                fake.requests
                    .single()
                    .url
                    .queryParameter("append_to_response"),
            )
        }

    @Test
    fun trailer_preference_order() {
        fun v(
            key: String,
            type: String,
            lang: String?,
            site: String = "YouTube",
        ) = TmdbRawVideo(key = key, site = site, type = type, language = lang)
        val fr = FRENCH.mediaLanguages
        assertEquals("a", pickTrailerKey(listOf(v("b", "Trailer", "en"), v("a", "Trailer", "fr")), fr))
        assertEquals("b", pickTrailerKey(listOf(v("c", "Teaser", "fr"), v("b", "Trailer", "en")), fr))
        assertEquals("c", pickTrailerKey(listOf(v("d", "Teaser", "en"), v("c", "Teaser", "fr")), fr))
        assertEquals("d", pickTrailerKey(listOf(v("d", "Teaser", "en"), v("x", "Featurette", "fr")), fr))
        assertNull(pickTrailerKey(listOf(v("x", "Trailer", "fr", site = "Vimeo")), fr))
        // An English interface prefers the English trailer and never picks a French one
        assertEquals("b", pickTrailerKey(listOf(v("a", "Trailer", "fr"), v("b", "Trailer", "en")), ENGLISH.mediaLanguages))
        assertNull(pickTrailerKey(listOf(v("a", "Trailer", "fr")), ENGLISH.mediaLanguages))
    }

    @Test
    fun logo_preference_follows_the_interface_language() {
        val logos = listOf(TmdbRawImage("/fr.png", "fr"), TmdbRawImage("/en.png", "en"), TmdbRawImage("/plain.png", null))
        assertEquals("/fr.png", pickLogoPath(logos, FRENCH.mediaLanguages))
        assertEquals("/en.png", pickLogoPath(logos, ENGLISH.mediaLanguages))
        assertEquals("/plain.png", pickLogoPath(logos.filter { it.language != "en" }, ENGLISH.mediaLanguages))
    }

    @Test
    fun details_are_asked_in_the_interface_language_and_cached_per_language() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/movie/27205" to fixture("movie_details.json")))
            var language = ENGLISH
            val client = client(fake).also { it.language = { language } }

            client.details(TmdbMediaType.MOVIE, 27205)
            val url = fake.requests.single().url
            assertEquals("en-US", url.queryParameter("language"))
            assertEquals("en", url.queryParameter("include_video_language"))
            assertEquals("en,null", url.queryParameter("include_image_language"))

            language = FRENCH
            client.details(TmdbMediaType.MOVIE, 27205)
            assertEquals(2, fake.requests.size)
            assertEquals(
                "fr-FR",
                fake.requests
                    .last()
                    .url
                    .queryParameter("language"),
            )
        }

    @Test
    fun details_are_cached() =
        runTest(testDispatcher) {
            val fake = Fake(mapOf("/3/movie/27205" to fixture("movie_details.json")))
            val c = client(fake)
            c.details(TmdbMediaType.MOVIE, 27205)
            c.details(TmdbMediaType.MOVIE, 27205)
            assertEquals(1, fake.requests.size)
        }
}
