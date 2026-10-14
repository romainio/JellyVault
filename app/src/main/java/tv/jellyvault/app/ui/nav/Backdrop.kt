@file:OptIn(ExperimentalCoilApi::class)

package tv.jellyvault.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import tv.jellyvault.app.preferences.BackdropStyle
import tv.jellyvault.app.services.BackdropResult
import tv.jellyvault.app.ui.CrossFadeFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows the current backdrop images provided by [tv.jellyvault.app.services.BackdropService]
 */
@Composable
fun Backdrop(
    drawerIsOpen: Boolean,
    backdropStyle: BackdropStyle,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    useExistingImageAsPlaceholder: Boolean = false,
    crossfadeDuration: Duration = 800.milliseconds,
) {
    val backdrop by viewModel.backdropService.backdropFlow.collectAsStateWithLifecycle()
    val dim by viewModel.backdropService.dimFlow.collectAsStateWithLifecycle()
    Backdrop(
        backdrop = backdrop,
        dim = dim.level,
        drawerIsOpen = drawerIsOpen,
        backdropStyle = backdropStyle,
        modifier = modifier,
        enableTopScrim = enableTopScrim,
        useExistingImageAsPlaceholder = useExistingImageAsPlaceholder,
        crossfadeDuration = crossfadeDuration,
    )
}

/**
 * Shows the current backdrop images provided by the [BackdropResult]
 */
@Composable
fun Backdrop(
    backdrop: BackdropResult,
    drawerIsOpen: Boolean,
    backdropStyle: BackdropStyle,
    modifier: Modifier = Modifier,
    dim: Float = 0f,
    enableTopScrim: Boolean = true,
    useExistingImageAsPlaceholder: Boolean = false,
    crossfadeDuration: Duration = 800.milliseconds,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val baseBackgroundColor = MaterialTheme.colorScheme.background
    val dimAlpha by animateFloatAsState(dim, tween(BackdropGeometry.DIM_MILLIS), label = "backdrop_dim")
    // Dynamic colors (4 full-screen radial gradients) were removed: too expensive for TV GPUs.
    // The image is drawn plainly, with gradient scrims painted on top (no offscreen layer, no blend modes).
    if (backdropStyle != BackdropStyle.BACKDROP_NONE) {
        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth()
                        .aspectRatio(BackdropGeometry.ASPECT),
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(backdrop.imageUrl)
                            .useExistingImageAsPlaceholder(useExistingImageAsPlaceholder)
                            .transitionFactory(CrossFadeFactory(minOf(crossfadeDuration, MAX_CROSSFADE)))
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val fadeEnd = size.width * BackdropGeometry.LEFT_FADE_END
                                // The image fills the screen: darken the text side (not a full fade) and fade out the bottom
                                drawRect(
                                    brush =
                                        Brush.horizontalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to baseBackgroundColor.copy(alpha = 0.95f),
                                                    0.45f to baseBackgroundColor.copy(alpha = 0.7f),
                                                    1f to Color.Transparent,
                                                ),
                                            startX = if (isRtl) size.width else 0f,
                                            endX = if (isRtl) size.width - fadeEnd else fadeEnd,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, baseBackgroundColor),
                                            startY = size.height * BackdropGeometry.BOTTOM_FADE_START,
                                            endY = size.height,
                                        ),
                                )
                            },
                )
            }
            // Screen-wide scrims: no visible edge where the image box starts
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            if (enableTopScrim) {
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to Color.Black.copy(alpha = TOP_SCRIM_ALPHA),
                                                    TOP_SCRIM_END_FRACTION to Color.Transparent,
                                                ),
                                        ),
                                )
                            }
                            if (dimAlpha > 0f) {
                                drawRect(color = baseBackgroundColor, alpha = dimAlpha)
                            }
                            if (drawerIsOpen) {
                                drawRect(color = Color.Black, alpha = .75f)
                            }
                        },
            )
        }
    }
}

/** Backdrop image: 16:9 across the whole screen, darkened on the text side */
object BackdropGeometry {
    const val ASPECT = 16f / 9f
    const val LEFT_FADE_END = 0.65f
    const val BOTTOM_FADE_START = 0.6f
    const val DIM_MILLIS = 300

    /** Grids of posters over the image */
    const val DIM_GRID = 0.7f

    /** A title page scrolled down to its rows */
    const val DIM_ROWS = 0.6f
}

/**
 * Darkens the app backdrop while this page shows [level] (0 = as is). Cleared when the page leaves
 */
@Composable
fun DimBackdrop(
    level: Float,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
) {
    val service = viewModel.backdropService
    val owner = remember { Any() }
    LaunchedEffect(level) { service.requestDim(owner, level) }
    DisposableEffect(Unit) { onDispose { service.releaseDim(owner) } }
}

private val MAX_CROSSFADE = 300.milliseconds

/** The header of a title page has mostly scrolled away: its rows now sit over the backdrop */
fun LazyListState.isScrolledToRows(): Boolean = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > SCROLLED_TO_ROWS_PX

private const val SCROLLED_TO_ROWS_PX = 24
