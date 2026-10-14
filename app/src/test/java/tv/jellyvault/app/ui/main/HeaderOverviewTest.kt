package tv.jellyvault.app.ui.main

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderOverviewTest {
    @Test
    fun overview_box_fits_its_lines_at_the_new_line_height() {
        // bodyMedium is 14 sp on a 21 sp line: 4 lines need 84 dp
        assertEquals(84.dp, headerOverviewHeight(twoLines = false))
        assertEquals(44.dp, headerOverviewHeight(twoLines = true))
        assertEquals(6, tv.jellyvault.app.ui.detail.DETAIL_OVERVIEW_LINES)
    }

    @Test
    fun header_is_tall_enough_for_logo_details_genres_and_four_lines() {
        // logo 60 + 4 + details 20 + 8 + genres 16 + 8 + overview 84 = 200 dp
        assertEquals(true, tv.jellyvault.app.ui.components.HeaderUtils.height >= 200.dp)
    }
}
