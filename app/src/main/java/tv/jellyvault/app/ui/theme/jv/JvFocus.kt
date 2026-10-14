package tv.jellyvault.app.ui.theme.jv

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import kotlin.math.roundToInt

object JvMotion {
    const val FOCUS_MILLIS = 180
    const val DRAWER_MILLIS = 200
    const val SHELF_MILLIS = 220

    /** Fast out: moves most of the way at once, then settles */
    val Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
}

object JvFocus {
    const val CARD_SCALE = 1.10f
    const val BUTTON_SCALE = 1.06f
    val BORDER = 2.dp
    val GLOW_ELEVATION = 18.dp
    const val GLOW_ALPHA = 0.6f
}

/**
 * [base] scaled by the system animator duration scale (0 when the user turned animations off)
 */
fun jvMillis(
    base: Int,
    animatorScale: Float,
): Int = (base * animatorScale.coerceAtLeast(0f)).roundToInt()

@Composable
fun rememberAnimatorScale(): Float {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
}

/**
 * Focus look of every card: bigger, white ring and a soft shadow on the focused card only.
 * No vertical lift: moving the focused card changes its position for D-pad navigation and Down then lands on the card beside it
 */
object JvCardDefaults {
    @Composable
    fun scale() = CardDefaults.scale(focusedScale = JvFocus.CARD_SCALE)

    @Composable
    fun border(shape: Shape = JvShapes.Poster) =
        CardDefaults.border(
            focusedBorder = Border(BorderStroke(JvFocus.BORDER, MaterialTheme.colorScheme.border), shape = shape),
        )

    @Composable
    fun glow() =
        CardDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = JvFocus.GLOW_ALPHA), elevation = JvFocus.GLOW_ELEVATION),
        )

    @Composable
    fun shape(shape: Shape = JvShapes.Poster) = CardDefaults.shape(shape = shape)
}
