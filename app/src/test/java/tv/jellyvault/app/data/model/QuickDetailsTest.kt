package tv.jellyvault.app.data.model

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jellyvault.app.ui.DOT_SEPARATOR
import tv.jellyvault.app.ui.components.isNetworkError
import tv.jellyvault.app.ui.dot
import tv.jellyvault.app.ui.visibleQuickDetailSegments
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

class QuickDetailsTest {
    private fun rating(text: String) =
        buildAnnotatedString {
            dot()
            append(text)
        }

    @Test
    fun blankDetailsAreSkipped() {
        assertEquals(listOf("2019", "1h 30m"), listOf("", "2019", null, "  ", "1h 30m").nonBlankDetails())
        assertEquals(emptyList<String>(), listOf<String?>("", " ").nonBlankDetails())
    }

    @Test
    fun noLeadingSeparatorWhenBasicDetailsAreEmpty() {
        val segments =
            visibleQuickDetailSegments(
                listOf(AnnotatedString(""), rating("FR-12"), null, rating("8.4")),
            )
        assertEquals(listOf("FR-12", DOT_SEPARATOR + "8.4"), segments.map { it.text })
    }

    @Test
    fun separatorsKeptAfterBasicDetails() {
        val segments = visibleQuickDetailSegments(listOf(AnnotatedString("2019"), rating("FR-12")))
        assertEquals(listOf("2019", DOT_SEPARATOR + "FR-12"), segments.map { it.text })
    }

    @Test
    fun networkErrorsAreDetectedInTheCauseChain() {
        assertTrue(ConnectException("refused").isNetworkError())
        assertTrue(RuntimeException("wrapped", UnknownHostException("jellyfin.local")).isNetworkError())
        assertTrue(IllegalStateException("x", IOException("reset")).isNetworkError())
        assertFalse(IllegalStateException("bad state").isNetworkError())
        assertFalse((null as Throwable?).isNetworkError())
    }
}
