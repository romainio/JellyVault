package tv.jellyvault.app.ui.preferences.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {
    @Test
    fun the_approval_url_becomes_a_square_code_with_its_finder_patterns() {
        val modules = qrModules("https://www.themoviedb.org/authenticate/0123456789abcdef0123456789abcdef01234567")
        val size = modules.size
        assertTrue(size >= 21)
        assertTrue(modules.all { it.size == size })
        // Top-left finder pattern: a dark 7x7 square border
        assertTrue((0 until 7).all { modules[0][it] && modules[it][0] && modules[6][it] && modules[it][6] })
        assertEquals(false, modules[1][1])
        assertTrue(modules[3][3])
    }
}
