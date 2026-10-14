package tv.jellyvault.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object HeaderUtils {
    val topPadding = 48.dp
    val bottomPadding = 16.dp
    val startPadding = 8.dp

    val padding = PaddingValues(top = topPadding, bottom = bottomPadding, start = startPadding)

    /** Logo, details, genres and a 4-line summary (200 dp); rows move down only 8 dp thanks to the smaller bottom padding */
    val height = 204.dp

    val logoHeight = 60.dp

    val modifier =
        Modifier
            .padding(padding)
            .height(height)
}
