package tv.jellyvault.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jellyvault.app.data.model.HomePageSettings
import tv.jellyvault.app.data.model.HomeRowConfig

class HomeSettingsUsableTest {
    @Test
    fun `a saved home without rows is treated as missing, so the default home comes back`() {
        assertNull(usable(HomePageSettings(rows = emptyList(), version = 1)))
        assertNull(usable(null))
    }

    @Test
    fun `a saved home with rows is kept`() {
        val settings = HomePageSettings(rows = listOf(HomeRowConfig.ContinueWatchingCombined()), version = 1)
        assertEquals(settings, usable(settings))
    }
}
