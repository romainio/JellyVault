package tv.jellyvault.app.ui.preferences.arr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrMaskingTest {
    @Test
    fun api_key_shows_only_last_four() {
        assertEquals("•••• cdef", maskApiKey("0123456789abcdef"))
        assertNull(maskApiKey(""))
    }

    @Test
    fun password_never_reveals_characters() {
        assertEquals("••••••••", maskPassword("secret-pass"))
        assertEquals("••••••••", maskPassword("ab"))
        assertNull(maskPassword(""))
    }
}
