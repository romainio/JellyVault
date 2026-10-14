package tv.jellyvault.app.catalog.library

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jellyvault.app.catalog.tmdb.TmdbCredential
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.JellyfinUser
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryIndexTest {
    private class FakeSource(
        var movies: List<BaseItemDto> = emptyList(),
        var series: List<BaseItemDto> = emptyList(),
        var fail: Boolean = false,
    ) : LibraryItemSource {
        val pageRequests = mutableListOf<Triple<BaseItemKind, Int, Int>>()

        override suspend fun fetchProviderIdPage(
            kind: BaseItemKind,
            startIndex: Int,
            limit: Int,
        ): ProviderIdPage {
            pageRequests += Triple(kind, startIndex, limit)
            if (fail) throw RuntimeException("server down")
            val all = if (kind == BaseItemKind.MOVIE) movies else series
            return ProviderIdPage(all.drop(startIndex).take(limit), all.size)
        }

        override suspend fun fetchItems(ids: List<UUID>): List<BaseItemDto> = (movies + series).filter { it.id in ids }

        override suspend fun requestLibraryScan(): Boolean = false
    }

    private fun dto(
        kind: BaseItemKind,
        providerIds: Map<String, String?>?,
        id: UUID = UUID.randomUUID(),
    ) = BaseItemDto(id = id, type = kind, providerIds = providerIds)

    private fun user(id: UUID = UUID.randomUUID()) = JellyfinUser(id = id, name = "u", serverId = UUID.randomUUID(), accessToken = "t")

    private companion object {
        const val TOKEN = "token"
    }

    private fun serverRepository(flow: MutableStateFlow<JellyfinUser?>) = mockk<ServerRepository> { every { currentUserFlow } returns flow }

    @Test
    fun builds_index_for_movies_and_series_across_pages() =
        runTest {
            val movies = List(LibraryIndex.PAGE_SIZE + 3) { dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "${1000 + it}")) }
            val show = dto(BaseItemKind.SERIES, mapOf("Tmdb" to "42"))
            val source = FakeSource(movies = movies, series = listOf(show))
            val index = LibraryIndex(source, serverRepository(MutableStateFlow(user())), backgroundScope, TmdbCredential { TOKEN })
            runCurrent()

            val map = index.index.value!!
            assertEquals(LibraryIndex.PAGE_SIZE + 4, map.size)
            assertEquals(movies[0].id, map[TmdbKey(TmdbMediaType.MOVIE, 1000)])
            assertEquals(show.id, map[TmdbKey(TmdbMediaType.TV, 42)])
            assertEquals(2, source.pageRequests.count { it.first == BaseItemKind.MOVIE })
        }

    @Test
    fun provider_id_parsing_is_tolerant() =
        runTest {
            val lower = dto(BaseItemKind.MOVIE, mapOf("tmdb" to "7"))
            val source =
                FakeSource(
                    movies =
                        listOf(
                            lower,
                            dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "abc")),
                            dto(BaseItemKind.MOVIE, mapOf("Imdb" to "tt1")),
                            dto(BaseItemKind.MOVIE, null),
                            dto(BaseItemKind.MOVIE, mapOf("Tmdb" to null)),
                        ),
                )
            val index = LibraryIndex(source, serverRepository(MutableStateFlow(user())), backgroundScope, TmdbCredential { TOKEN })
            runCurrent()

            assertEquals(mapOf(TmdbKey(TmdbMediaType.MOVIE, 7) to lower.id), index.index.value)
        }

    @Test
    fun failure_keeps_previous_index() =
        runTest {
            val movie = dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "1"))
            val source = FakeSource(movies = listOf(movie))
            val index = LibraryIndex(source, serverRepository(MutableStateFlow(user())), backgroundScope, TmdbCredential { TOKEN })
            runCurrent()
            source.fail = true
            index.refresh()
            assertEquals(movie.id, index.index.value!![TmdbKey(TmdbMediaType.MOVIE, 1)])
        }

    @Test
    fun failed_first_load_is_retried_after_backoff() =
        runTest {
            var time = 1_000_000L
            val movie = dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "1"))
            val source = FakeSource(movies = listOf(movie), fail = true)
            val index = LibraryIndex(source, serverRepository(MutableStateFlow(user())), backgroundScope, TmdbCredential { TOKEN })
            index.now = { time }
            runCurrent()
            assertNull(index.index.value)

            // Within the backoff window: no new attempt
            source.fail = false
            val requestsAfterFailure = source.pageRequests.size
            time += LibraryIndex.RETRY_BACKOFF_MS / 2
            assertEquals(emptyMap<TmdbKey, UUID>(), index.awaitIndex(timeoutMs = 10))
            runCurrent()
            assertEquals(requestsAfterFailure, source.pageRequests.size)
            assertNull(index.index.value)

            // After the backoff: awaitIndex triggers a reload which succeeds
            time += LibraryIndex.RETRY_BACKOFF_MS
            index.awaitIndex(timeoutMs = 10)
            runCurrent()
            assertEquals(mapOf(TmdbKey(TmdbMediaType.MOVIE, 1) to movie.id), index.index.value)
        }

    @Test
    fun no_tmdb_token_means_no_library_load() =
        runTest {
            val source = FakeSource(movies = listOf(dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "1"))))
            val index = LibraryIndex(source, serverRepository(MutableStateFlow(user())), backgroundScope, TmdbCredential { "" })
            runCurrent()
            assertEquals(emptyList<Triple<BaseItemKind, Int, Int>>(), source.pageRequests)
            assertNull(index.index.value)
            assertEquals(emptyMap<TmdbKey, UUID>(), index.awaitIndex())
        }

    @Test
    fun a_key_entered_after_startup_builds_the_index() =
        runTest {
            var credential = ""
            val movie = dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "1"))
            val source = FakeSource(movies = listOf(movie))
            val index = LibraryIndex(source, serverRepository(MutableStateFlow(user())), backgroundScope, TmdbCredential { credential })
            runCurrent()
            assertEquals(emptyMap<TmdbKey, UUID>(), index.awaitIndex(timeoutMs = 10))
            assertEquals(emptyList<Triple<BaseItemKind, Int, Int>>(), source.pageRequests)

            credential = TOKEN
            val owned = backgroundScope.async { index.awaitIndex() }
            runCurrent()

            assertEquals(mapOf(TmdbKey(TmdbMediaType.MOVIE, 1) to movie.id), owned.await())
        }

    @Test
    fun a_cleared_key_stops_the_index() =
        runTest {
            var credential = TOKEN
            val source = FakeSource(movies = listOf(dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "1"))))
            val index = LibraryIndex(source, serverRepository(MutableStateFlow(user())), backgroundScope, TmdbCredential { credential })
            runCurrent()
            assertEquals(1, index.awaitIndex().size)

            credential = ""
            assertEquals(emptyMap<TmdbKey, UUID>(), index.awaitIndex())
        }

    @Test
    fun no_user_means_no_index() =
        runTest {
            val index = LibraryIndex(FakeSource(), serverRepository(MutableStateFlow(null)), backgroundScope, TmdbCredential { TOKEN })
            runCurrent()
            assertNull(index.index.value)
            assertEquals(emptyMap<TmdbKey, UUID>(), index.awaitIndex(timeoutMs = 10))
        }

    @Test
    fun switching_user_resets_and_reloads() =
        runTest {
            val userFlow = MutableStateFlow<JellyfinUser?>(user())
            val first = dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "1"))
            val source = FakeSource(movies = listOf(first))
            val index = LibraryIndex(source, serverRepository(userFlow), backgroundScope, TmdbCredential { TOKEN })
            runCurrent()

            val second = dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "2"))
            source.movies = listOf(second)
            userFlow.value = user()
            runCurrent()

            assertEquals(mapOf(TmdbKey(TmdbMediaType.MOVIE, 2) to second.id), index.index.value)
        }

    /**
     * Reproduces the cross-user race from code review: a refresh started for user A - such as the
     * detached, not-cancelled-on-switch background refresh that [LibraryIndex.awaitIndex] can trigger -
     * is still in flight, blocked mid-fetch, when the current user changes to B. Because [LibraryIndex]
     * serializes refreshes with a single mutex, the stale A refresh (which grabbed the mutex first) is
     * always the one to finish first once unblocked, with B's own reload queued right behind it. So the
     * bug is not that the final published index is wrong (B's refresh always runs last and overwrites
     * it), it is that the stale refresh briefly publishes A's data *after* the switch, before B's reload
     * gets a chance to run - a real, user-visible data leak. This test records every value published on
     * [LibraryIndex.index] and asserts A's data is never observed once the switch to B has happened.
     */
    @Test
    fun stale_refresh_does_not_overwrite_newer_user_index() =
        runTest {
            val userA = user()
            val movieA = dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "1"))
            val movieB = dto(BaseItemKind.MOVIE, mapOf("Tmdb" to "2"))
            val gate = CompletableDeferred<Unit>()
            var gateArmed = false

            // Movie calls are answered by call order, not by re-reading mutable state after the gate
            // opens: a real network response reflects the credentials active when the request was
            // *sent*, not whatever the ambient ApiClient auth becomes by the time it completes. The
            // first two MOVIE calls (the initial load, then the stale detached one) represent requests
            // sent while A was current and so both resolve to A's data; anything after that is B's.
            var movieCallCount = 0
            val source =
                object : LibraryItemSource {
                    override suspend fun fetchProviderIdPage(
                        kind: BaseItemKind,
                        startIndex: Int,
                        limit: Int,
                    ): ProviderIdPage {
                        val all =
                            if (kind == BaseItemKind.MOVIE) {
                                if (movieCallCount++ < 2) listOf(movieA) else listOf(movieB)
                            } else {
                                emptyList()
                            }
                        if (gateArmed) {
                            gateArmed = false
                            gate.await()
                        }
                        return ProviderIdPage(all.drop(startIndex).take(limit), all.size)
                    }

                    override suspend fun fetchItems(ids: List<UUID>): List<BaseItemDto> = emptyList()

                    override suspend fun requestLibraryScan(): Boolean = false
                }

            val userFlow = MutableStateFlow<JellyfinUser?>(userA)
            val index = LibraryIndex(source, serverRepository(userFlow), backgroundScope, TmdbCredential { TOKEN })
            runCurrent()
            assertEquals(movieA.id, index.index.value!![TmdbKey(TmdbMediaType.MOVIE, 1)])

            val seenMaps = mutableListOf<Map<TmdbKey, UUID>?>()
            backgroundScope.launch { index.index.collect { seenMaps += it } }
            runCurrent()

            // Simulate a stale, detached refresh for user A (as awaitIndex()'s background refresh
            // would be): started while A is current, but blocked mid-fetch, holding the mutex.
            gateArmed = true
            backgroundScope.launch { index.refresh() }
            runCurrent()

            // The user switches to B while that stale refresh is still parked on the gate. This resets
            // the index and queues B's own refresh, which cannot proceed until the mutex is freed.
            userFlow.value = user()
            runCurrent()
            assertNull("switch must reset the index before any reload completes", index.index.value)

            // Unblock the stale A refresh; it (and, immediately after, B's queued refresh) finish.
            gate.complete(Unit)
            runCurrent()

            assertEquals(movieB.id, index.index.value!![TmdbKey(TmdbMediaType.MOVIE, 2)])
            val resetIndex = seenMaps.indexOf(null)
            assertEquals("expected the reset (null) to have been recorded", true, resetIndex >= 0)
            val publishedAfterSwitch = seenMaps.subList(resetIndex + 1, seenMaps.size)
            assertEquals(
                "user A's stale data must never be published once B is current",
                emptyList<Map<TmdbKey, UUID>?>(),
                publishedAfterSwitch.filter { it != null && it.containsKey(TmdbKey(TmdbMediaType.MOVIE, 1)) },
            )
        }
}
