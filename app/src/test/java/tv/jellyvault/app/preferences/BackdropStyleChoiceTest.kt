package tv.jellyvault.app.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class BackdropStyleChoiceTest {
    private val pref = AppPreference.BackdropStylePref

    @Test
    fun only_image_and_none_are_offered() {
        assertEquals(BackdropStyle.BACKDROP_IMAGE_ONLY, pref.indexToValue(0))
        assertEquals(BackdropStyle.BACKDROP_NONE, pref.indexToValue(1))
        assertEquals(BackdropStyle.BACKDROP_IMAGE_ONLY, pref.defaultValue)
    }

    @Test
    fun legacy_dynamic_color_is_shown_as_image() {
        assertEquals(0, pref.valueToIndex(BackdropStyle.BACKDROP_DYNAMIC_COLOR))
        assertEquals(0, pref.valueToIndex(BackdropStyle.BACKDROP_IMAGE_ONLY))
        assertEquals(1, pref.valueToIndex(BackdropStyle.BACKDROP_NONE))
    }
}
