package tv.jellyvault.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyNamesTest {
    @Test
    fun `names stored on devices and servers never change`() {
        assertEquals("wholphin", LegacyNames.DATABASE)
        assertEquals("Wholphin", LegacyNames.DISPLAY_PREFS_CLIENT)
        assertEquals("Wholphin (Debug)", LegacyNames.DISPLAY_PREFS_CLIENT_DEBUG)
    }

    @Test
    fun `names of the old project that the upgrade migration looks for never change`() {
        assertEquals(
            listOf(
                "com.github.damontecres.wholphin.services.SuggestionsWorker",
                "com.github.damontecres.wholphin.services.LatestNextUpWorker",
                "com.github.damontecres.wholphin.services.tvprovider.TvProviderWorker",
            ),
            LegacyNames.WORK_NAMES,
        )
        assertEquals("https://api.github.com/repos/damontecres/Wholphin/releases/latest", LegacyNames.UPDATE_URL)
    }
}
