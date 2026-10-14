package tv.jellyvault.app.ui.preferences.arr

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.arr.ArrException
import java.io.IOException

class ArrErrorMessageTest {
    @Test
    fun each_error_has_a_distinct_explanation() {
        assertEquals(R.string.arr_error_basic_auth, ArrException.Unauthorized(401, basicAuth = true).messageRes())
        assertEquals(R.string.arr_error_unauthorized, ArrException.Unauthorized(401).messageRes())
        assertEquals(R.string.arr_error_invalid_settings, ArrException.InvalidSettings().messageRes())
        assertEquals(R.string.arr_error_unreachable, ArrException.Unreachable(IOException()).messageRes())
        assertEquals(R.string.arr_error_http, ArrException.Http(500).messageRes())
    }
}
