package tv.jellyvault.app.ui.theme.jv

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JvFocusTest {
    @Test
    fun focus_tokens_match_the_spec() {
        assertEquals(1.10f, JvFocus.CARD_SCALE, 0.001f)
        assertEquals(1.06f, JvFocus.BUTTON_SCALE, 0.001f)
        assertEquals(2.dp, JvFocus.BORDER)
        assertEquals(180, JvMotion.FOCUS_MILLIS)
        assertEquals(200, JvMotion.DRAWER_MILLIS)
        assertEquals(1f, JvMotion.Easing.transform(1f), 0.001f)
    }

    @Test
    fun easing_is_fast_out() {
        // A quick ease-out is already well past half way at a third of the time
        assertTrue(JvMotion.Easing.transform(1f / 3f) > 0.7f)
    }

    @Test
    fun durations_are_zero_when_animations_are_off() {
        assertEquals(180, jvMillis(180, 1f))
        assertEquals(90, jvMillis(180, 0.5f))
        assertEquals(0, jvMillis(180, 0f))
    }
}
