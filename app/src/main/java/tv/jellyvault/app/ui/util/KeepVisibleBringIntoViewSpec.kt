package tv.jellyvault.app.ui.util

import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Scroll only when the focused element is not fully visible, and only by as much as needed.
 * Used for the top block of a detail page: focusing its buttons must not push the title off screen.
 */
object KeepVisibleBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float =
        when {
            offset < 0f -> offset
            offset + size > containerSize -> offset + size - containerSize
            else -> 0f
        }
}
