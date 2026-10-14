package tv.jellyvault.app.catalog.tmdb

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TmdbCredentialTest {
    @get:Rule val tmp = TemporaryFolder()

    private val v3 = "0123456789abcdef0123456789ABCDEF"

    @Test
    fun `a 32 hex key is a v3 api key, anything else a v4 token, blank is nothing`() {
        assertEquals(TmdbAuth.ApiKey(v3), TmdbAuth.of("  $v3\n"))
        assertEquals(TmdbAuth.Bearer("eyJhbGciOiJIUzI1NiJ9.x.y"), TmdbAuth.of("eyJhbGciOiJIUzI1NiJ9.x.y "))
        assertNull(TmdbAuth.of(" \n"))
    }

    @Test
    fun `a malformed credential is nothing rather than a crash`() {
        assertNull(TmdbAuth.of("abc def"))
        assertNull(TmdbAuth.of("ab\ncd"))
        assertNull(TmdbAuth.of("\u201Cabc\u201D"))
        assertNull(TmdbAuth.of("caf\u00E9"))
    }

    @Test
    fun `a v3 key goes in the query, a v4 token in the header`() {
        val url = "https://api.themoviedb.org/3/trending/movie/week".toHttpUrl()
        val keyed = Request.Builder().tmdbRequest(url, v3).build()
        assertEquals(v3, keyed.url.queryParameter("api_key"))
        assertNull(keyed.header("Authorization"))

        val bearer = Request.Builder().tmdbRequest(url, "tok").build()
        assertEquals("Bearer tok", bearer.header("Authorization"))
        assertNull(bearer.url.queryParameter("api_key"))
    }

    @Test
    fun `the user's key wins over the build key, and clearing it brings the build key back`() {
        val file = File(tmp.root, "tmdb_key")
        val store = TmdbKeyStore(file, buildDefault = "build")
        assertEquals("build", store.current())
        assertTrue(store.usesBuildDefault)

        store.save("  mine \n")
        assertEquals("mine", store.current())
        assertFalse(store.usesBuildDefault)
        assertEquals("mine", TmdbKeyStore(file, buildDefault = "build").current())

        store.save("")
        assertEquals("build", store.current())
        assertFalse(file.exists())
    }

    @Test
    fun `no key anywhere means not configured`() {
        assertEquals("", TmdbKeyStore(File(tmp.root, "none"), buildDefault = "").current())
    }
}
