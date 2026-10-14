package tv.jellyvault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.jellyvault.app.ui.theme.jv.JvColors
import tv.jellyvault.app.ui.theme.jv.JvShapes

/**
 * A short confirmation ("Demandé à Radarr", "Note enregistrée"…)
 */
data class JvBannerMessage(
    val text: String,
    val isError: Boolean = false,
)

/**
 * In-app replacement for a Toast: a small banner at the bottom of the screen that hides itself after [durationMillis].
 * It lives in a non-focusable popup, so it can be shown from anywhere (even inside a row of buttons) and never takes
 * the D-pad focus.
 */
@Composable
fun JvBanner(
    message: JvBannerMessage?,
    onDismiss: () -> Unit,
    durationMillis: Long = 3_000,
) {
    message ?: return
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        delay(durationMillis)
        currentOnDismiss()
    }
    val marginPx = with(LocalDensity.current) { 32.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(marginPx) { BottomOfWindow(marginPx) },
        properties = PopupProperties(focusable = false),
    ) {
        val visible = remember(message) { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(visibleState = visible, enter = fadeIn()) {
            Surface(
                shape = JvShapes.Button,
                colors =
                    SurfaceDefaults.colors(
                        containerColor = if (message.isError) JvColors.ErrorContainer else JvColors.SurfaceHigh,
                        contentColor = JvColors.Text,
                    ),
                modifier = Modifier.widthIn(max = 640.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Bottom center of the window wherever the banner is declared (a plain Popup aligns on its parent) */
private class BottomOfWindow(
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset(
        (windowSize.width - popupContentSize.width) / 2,
        windowSize.height - popupContentSize.height - marginPx,
    )
}
