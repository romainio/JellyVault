package tv.jellyvault.app.catalog.taste

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.catalog.CatalogRepository
import tv.jellyvault.app.catalog.MediaCard
import tv.jellyvault.app.catalog.library.TmdbKey
import tv.jellyvault.app.catalog.tmdb.TmdbClient
import tv.jellyvault.app.catalog.tmdb.TmdbException
import tv.jellyvault.app.catalog.tmdb.TmdbItem
import tv.jellyvault.app.catalog.tmdb.TmdbLanguage
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType

class RecommenderTest {
    private val movie = TmdbMediaType.MOVIE
    private val tmdb = mockk<TmdbClient>()
    private val catalog = mockk<CatalogRepository>()
    private var history = TasteHistory(emptyList(), emptySet())
    private var calls = 0
    private var user: java.util.UUID? = java.util.UUID.randomUUID()
    private var tasteVersion = 0L
    private val taste =
        object : TasteSource {
            override val userId: java.util.UUID? get() = user

            override fun version() = tasteVersion

            override suspend fun history(mediaType: TmdbMediaType): TasteHistory {
                calls++
                return history
            }
        }
    private val affinityStore = mockk<TasteAffinityStore>(relaxed = true)
    private val recommender = Recommender(taste, tmdb, catalog, affinityStore)

    private fun item(id: Int) = TmdbItem(id, movie, "T$id", null, "/p.jpg", null, "2020-01-01", 7.5, emptyList(), "en", 1000, 10.0)

    private fun seed(
        id: Int,
        weight: Double = 1.0,
    ) = TasteSeed(TmdbKey(movie, id), "Seed$id", weight)

    init {
        coEvery { catalog.cards(any()) } answers { firstArg<List<TmdbItem>>().map { MediaCard.Remote(it) } }
    }

    @Test
    fun for_you_ranks_titles_recommended_by_several_seeds_and_skips_watched() =
        runTest {
            history = TasteHistory(listOf(seed(1), seed(2)), setOf(TmdbKey(movie, 50)))
            coEvery { tmdb.recommendations(movie, 1) } returns listOf(item(10), item(11), item(50))
            coEvery { tmdb.recommendations(movie, 2) } returns listOf(item(12), item(11))
            assertEquals(
                11,
                recommender
                    .forYou(movie)
                    .first()
                    .tmdb
                    ?.tmdbId,
            )
            assertTrue(recommender.forYou(movie).none { it.tmdb?.tmdbId == 50 })
        }

    @Test
    fun a_failing_seed_does_not_break_the_others() =
        runTest {
            history = TasteHistory(listOf(seed(1), seed(2)), emptySet())
            coEvery { tmdb.recommendations(movie, 1) } throws TmdbException("down")
            coEvery { tmdb.recommendations(movie, 2) } returns listOf(item(12))
            assertEquals(listOf(12), recommender.forYou(movie).map { it.tmdb?.tmdbId })
        }

    @Test
    fun no_history_gives_nothing_and_is_not_cached() =
        runTest {
            assertTrue(recommender.forYou(movie).isEmpty())
            assertTrue(recommender.because(movie).isEmpty())
            assertEquals(2, calls)
        }

    @Test
    fun results_are_computed_once_for_all_rows() =
        runTest {
            history = TasteHistory(listOf(seed(1)), emptySet())
            coEvery { tmdb.recommendations(movie, 1) } returns (10..40).map { item(it) }
            recommender.forYou(movie)
            recommender.because(movie)
            recommender.forYou(movie)
            assertEquals(1, calls)
            coVerify(exactly = 1) { tmdb.recommendations(movie, 1) }
        }

    @Test
    fun another_interface_language_recomputes() =
        runTest {
            val previous = TmdbLanguage.profileLanguageTag
            try {
                history = TasteHistory(listOf(seed(1)), emptySet())
                coEvery { tmdb.recommendations(movie, 1) } returns listOf(item(10))
                TmdbLanguage.profileLanguageTag = { "fr" }
                recommender.forYou(movie)
                recommender.forYou(movie)
                TmdbLanguage.profileLanguageTag = { "en" }
                recommender.forYou(movie)
                assertEquals(2, calls)
            } finally {
                TmdbLanguage.profileLanguageTag = previous
            }
        }

    @Test
    fun cache_expires() =
        runTest {
            var time = 0L
            recommender.now = { time }
            history = TasteHistory(listOf(seed(1)), emptySet())
            coEvery { tmdb.recommendations(movie, 1) } returns listOf(item(10))
            recommender.forYou(movie)
            time = Recommender.TTL_MS + 1
            recommender.forYou(movie)
            assertEquals(2, calls)
        }

    @Test
    fun because_rows_exclude_what_for_you_already_shows() =
        runTest {
            history = TasteHistory(listOf(seed(1, 2.0), seed(2, 1.0)), emptySet())
            coEvery { tmdb.recommendations(movie, 1) } returns (10..49).map { item(it) }
            coEvery { tmdb.recommendations(movie, 2) } returns (10..49).map { item(it) }
            val forYou = recommender.forYou(movie).map { it.tmdb!!.tmdbId }.toSet()
            val because = recommender.because(movie)
            assertTrue(because.isNotEmpty())
            because.forEach { row -> assertTrue(row.cards.none { it.tmdb!!.tmdbId in forYou }) }
            assertEquals("Seed1", because.first().seedTitle)
        }

    @Test
    fun seeds_without_a_title_get_no_because_row() =
        runTest {
            history = TasteHistory(listOf(TasteSeed(TmdbKey(movie, 1), "", 2.0)), emptySet())
            coEvery { tmdb.recommendations(movie, 1) } returns (10..49).map { item(it) }
            assertTrue(recommender.because(movie).isEmpty())
        }

    @Test
    fun another_user_never_sees_the_previous_users_recommendations() =
        runTest {
            history = TasteHistory(listOf(seed(1)), emptySet())
            coEvery { tmdb.recommendations(movie, 1) } returns listOf(item(10))
            recommender.forYou(movie)
            user = java.util.UUID.randomUUID()
            history = TasteHistory(listOf(seed(2)), emptySet())
            coEvery { tmdb.recommendations(movie, 2) } returns listOf(item(20))
            assertEquals(listOf(20), recommender.forYou(movie).map { it.tmdb?.tmdbId })
        }

    @Test
    fun the_taste_affinity_is_saved_for_the_home_plan() =
        runTest {
            history = TasteHistory(listOf(seed(1)), emptySet())
            coEvery { tmdb.recommendations(movie, 1) } returns listOf(item(10).copy(genreIds = listOf(27)))
            recommender.forYou(movie)
            coVerify { affinityStore.save(user!!, mapOf(27 to 1.0)) }
        }

    @Test
    fun for_you_uses_the_disliked_titles_and_asks_for_well_rated_titles() =
        runTest {
            history = TasteHistory(listOf(seed(1)), emptySet(), negative = listOf(seed(2, 3.0)))
            coEvery { tmdb.recommendations(movie, 1) } returns listOf(item(10), item(11), item(12).copy(voteAverage = 6.0))
            coEvery { tmdb.recommendations(movie, 2) } returns listOf(item(10))
            assertEquals(listOf(11), recommender.forYou(movie).map { it.tmdb?.tmdbId })
            coVerify(exactly = 1) { tmdb.recommendations(movie, 2) }
        }

    @Test
    fun new_ratings_recompute_at_once() =
        runTest {
            history = TasteHistory(listOf(seed(1)), emptySet())
            coEvery { tmdb.recommendations(movie, 1) } returns listOf(item(10))
            recommender.forYou(movie)
            history = TasteHistory(listOf(seed(1)), setOf(TmdbKey(movie, 10)))
            assertEquals(listOf(10), recommender.forYou(movie).map { it.tmdb?.tmdbId })
            tasteVersion = 1
            assertTrue(recommender.forYou(movie).isEmpty())
        }
}
