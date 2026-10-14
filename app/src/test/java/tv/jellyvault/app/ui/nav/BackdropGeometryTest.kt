package tv.jellyvault.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class BackdropGeometryTest {
    @Test
    fun geometry_matches_the_spec() {
        assertEquals(16f / 9f, BackdropGeometry.ASPECT, 0.001f)
        assertEquals(0.65f, BackdropGeometry.LEFT_FADE_END, 0.001f)
        assertEquals(0.6f, BackdropGeometry.BOTTOM_FADE_START, 0.001f)
    }
}
