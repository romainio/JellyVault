// This file was inspired by related Button source files from
// https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/tv/tv-material/src/main/java/androidx/tv/material3

package tv.jellyvault.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.jellyvault.app.ui.theme.jv.JvFocus
import tv.jellyvault.app.ui.theme.jv.JvShapes

enum class ButtonStyle { Primary, Secondary }

data class ButtonPalette(
    val container: Color,
    val content: Color,
    val focusedContainer: Color,
    val focusedContent: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
)

/**
 * Button colors: primary is solid, secondary is glass until focused (theme colors, so every theme stays readable)
 */
fun buttonPalette(
    style: ButtonStyle,
    onSurface: Color,
    inverseOnSurface: Color,
): ButtonPalette =
    when (style) {
        // Only the focused button is solid: the main one is a brighter glass at rest, so focus never hides behind it.
        // Disabled primary shows a status ("Téléchargement 42 %"): glass with light text so it stays readable
        ButtonStyle.Primary -> {
            ButtonPalette(
                onSurface.copy(alpha = 0.24f),
                onSurface,
                onSurface,
                inverseOnSurface,
                onSurface.copy(alpha = 0.14f),
                onSurface.copy(alpha = 0.7f),
            )
        }

        ButtonStyle.Secondary -> {
            ButtonPalette(
                onSurface.copy(alpha = 0.14f),
                onSurface,
                onSurface,
                inverseOnSurface,
                onSurface.copy(alpha = 0.07f),
                onSurface.copy(alpha = 0.4f),
            )
        }
    }

/**
 * Player controls (video and music): just the icon at rest, a soft glass square under a full-white, bigger icon on focus.
 * A solid white square would hide the white icon and flash over the picture
 */
object PlayerButtonDefaults {
    const val FOCUSED_SCALE = 1.2f

    @Composable
    fun colors(): ClickableSurfaceColors {
        val onSurface = MaterialTheme.colorScheme.onSurface
        return ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = onSurface.copy(alpha = 0.8f),
            focusedContainerColor = onSurface.copy(alpha = 0.2f),
            focusedContentColor = onSurface,
            pressedContainerColor = onSurface.copy(alpha = 0.3f),
            pressedContentColor = onSurface,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = onSurface.copy(alpha = 0.3f),
        )
    }

    @Composable
    fun scale() = ClickableSurfaceDefaults.scale(focusedScale = FOCUSED_SCALE)

    @Composable
    fun border() = ClickableSurfaceDefaults.border()
}

/**
 * This is a re-implementation of [androidx.tv.material3.Button] with altered sizing, padding, colors, etc
 *
 * This allows for creating smaller Buttons with slightly rounded corners.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle.Secondary,
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(focusedScale = JvFocus.BUTTON_SCALE),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    shape: ClickableSurfaceShape =
        ClickableSurfaceDefaults.shape(
            shape = JvShapes.Button,
        ),
    colors: ClickableSurfaceColors =
        buttonPalette(
            style,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.inverseOnSurface,
        ).let { p ->
            ClickableSurfaceDefaults.colors(
                containerColor = p.container,
                contentColor = p.content,
                focusedContainerColor = p.focusedContainer,
                focusedContentColor = p.focusedContent,
                pressedContainerColor = p.focusedContainer,
                pressedContentColor = p.focusedContent,
                disabledContainerColor = p.disabledContainer,
                disabledContentColor = p.disabledContent,
            )
        },
    tonalElevation: Dp = 0.dp,
    border: ClickableSurfaceBorder =
        ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(JvFocus.BORDER, MaterialTheme.colorScheme.border), shape = JvShapes.Button),
            pressedBorder = Border.None,
            disabledBorder = Border.None,
            focusedDisabledBorder =
                Border(
                    border =
                        BorderStroke(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        ),
                    shape = JvShapes.Button,
                ),
        ),
    contentPadding: PaddingValues = DefaultButtonPadding,
    contentHeight: Dp = MinButtonSize,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.semantics { role = Role.Button },
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        border = border,
        interactionSource = interactionSource,
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
            Row(
                modifier =
                    Modifier
                        .padding(contentPadding)
                        .height(contentHeight),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
@NonRestartableComposable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues =
        PaddingValues(
            start = 8.dp,
            top = 4.dp,
            end = 8.dp,
            bottom = 4.dp,
        ),
    contentHeight: Dp = 32.dp,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = Button(
    onClick = onClick,
    modifier = modifier,
    onLongClick = onLongClick,
    enabled = enabled,
    contentPadding = contentPadding,
    contentHeight = contentHeight,
    interactionSource = interactionSource,
    content = content,
)

@Composable
fun TextButton(
    @StringRes stringRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues =
        PaddingValues(
            start = 8.dp,
            top = 4.dp,
            end = 8.dp,
            bottom = 4.dp,
        ),
    contentHeight: Dp = 32.dp,
    interactionSource: MutableInteractionSource? = null,
) = Button(
    onClick = onClick,
    modifier = modifier,
    onLongClick = onLongClick,
    enabled = enabled,
    contentPadding = contentPadding,
    contentHeight = contentHeight,
    interactionSource = interactionSource,
    content = {
        Text(text = stringResource(stringRes))
    },
)

val DefaultButtonPadding =
    PaddingValues(
        start = 4.dp,
        top = 4.dp,
        end = 4.dp,
        bottom = 4.dp,
    )
