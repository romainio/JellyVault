package tv.jellyvault.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.jellyvault.app.ui.AspectRatios
import tv.jellyvault.app.ui.Cards
import tv.jellyvault.app.ui.cards.ItemRowTitle
import tv.jellyvault.app.ui.theme.jv.JvColors
import tv.jellyvault.app.ui.theme.jv.JvShapes

/**
 * A home row still loading: its title over grey poster placeholders the size of the real cards.
 * The row stays focusable (like the text row it replaces) so D-pad navigation keeps a target while it loads
 */
@Composable
fun PlaceholderItemRow(
    title: String,
    modifier: Modifier = Modifier,
    cardHeight: Dp = Cards.height2x3,
    aspectRatio: Float = AspectRatios.TALL,
    spacing: Dp = 16.dp,
    count: Int = 8,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // One cheap alpha pulse for the whole row: no per-card animation, no gradient
    val pulse by rememberInfiniteTransition(label = "placeholder").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "placeholder alpha",
    )
    val cardColor = if (focused) JvColors.SurfaceHigh else JvColors.Surface
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.focusable(interactionSource = interactionSource),
    ) {
        ItemRowTitle(title)
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .horizontalScroll(rememberScrollState(), enabled = false)
                    .padding(horizontal = spacing, vertical = 8.dp)
                    .graphicsLayer { alpha = pulse },
        ) {
            repeat(count) {
                Box(
                    Modifier
                        .height(cardHeight)
                        .width(cardHeight * aspectRatio)
                        .background(cardColor, JvShapes.Poster),
                )
            }
        }
    }
}
