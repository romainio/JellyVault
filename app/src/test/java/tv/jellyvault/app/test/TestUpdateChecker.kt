package tv.jellyvault.app.test

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import tv.jellyvault.app.services.getDownloadUrl
import java.nio.file.Paths
import kotlin.io.path.readText

class TestUpdateChecker {
    lateinit var releaseJson: JsonObject
    val assetsJson: JsonArray by lazy { releaseJson["assets"]!!.jsonArray }

    @Before
    fun setup() {
        val resource = javaClass.classLoader?.getResource("release_develop.json")
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        releaseJson = Json.parseToJsonElement(fileContents).jsonObject
    }

    @Test
    fun `Release chooses release`() {
        val url = getDownloadUrl(assetsJson, false, listOf())
        Assert.assertEquals("https://github.com/romainio/JellyVault/releases/download/develop/JellyVault-release.apk", url)
    }

    @Test
    fun `Choose abi`() {
        val url = getDownloadUrl(assetsJson, false, listOf("arm64-v8a"))
        Assert.assertEquals(
            "https://github.com/romainio/JellyVault/releases/download/develop/JellyVault-release-arm64-v8a.apk",
            url,
        )
    }

    @Test
    fun `Choose unknown abi`() {
        val url = getDownloadUrl(assetsJson, false, listOf("unknown"))
        Assert.assertEquals("https://github.com/romainio/JellyVault/releases/download/develop/JellyVault-release.apk", url)
    }

    @Test
    fun `Debug chooses debug`() {
        val url = getDownloadUrl(assetsJson, true, listOf())
        Assert.assertEquals("https://github.com/romainio/JellyVault/releases/download/develop/JellyVault-debug.apk", url)
    }

    @Test
    fun `Choose debug abi`() {
        val url = getDownloadUrl(assetsJson, true, listOf("arm64-v8a"))
        Assert.assertEquals("https://github.com/romainio/JellyVault/releases/download/develop/JellyVault-debug-arm64-v8a.apk", url)
    }

    @Test
    fun `Release workflow short names choose the abi then the universal apk`() {
        val assets =
            Json
                .parseToJsonElement(
                    """
                    [{"name":"JellyVault.apk","browser_download_url":"https://example.com/JellyVault.apk"},
                     {"name":"JellyVault-arm64-v8a.apk","browser_download_url":"https://example.com/JellyVault-arm64-v8a.apk"},
                     {"name":"JellyVault-default-release-1.1.0-100-arm64-v8a.apk","browser_download_url":"https://example.com/long.apk"}]
                    """.trimIndent(),
                ).jsonArray
        Assert.assertEquals("https://example.com/JellyVault-arm64-v8a.apk", getDownloadUrl(assets, false, listOf("arm64-v8a")))
        Assert.assertEquals("https://example.com/JellyVault.apk", getDownloadUrl(assets, false, listOf("x86")))
        Assert.assertNull(getDownloadUrl(assets, true, listOf("arm64-v8a")))
    }
}
