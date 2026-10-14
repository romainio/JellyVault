package tv.jellyvault.app.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.ui.AppColors
import tv.jellyvault.app.ui.Cards
import tv.jellyvault.app.ui.LocalImageUrlService
import tv.jellyvault.app.ui.components.EpisodeName
import tv.jellyvault.app.ui.components.QuickDetails
import tv.jellyvault.app.ui.components.rememberLogoUrl
import tv.jellyvault.app.ui.isNotNullOrBlank
import tv.jellyvault.app.ui.theme.jv.JvCardDefaults
import tv.jellyvault.app.ui.theme.jv.JvMotion
import tv.jellyvault.app.ui.theme.jv.JvShapes
import tv.jellyvault.app.ui.theme.jv.jvMillis
import tv.jellyvault.app.ui.theme.jv.rememberAnimatorScale

/** Sizes of the modern home rows: posters at rest, the focused title opens to 16:9 */
object Shelf {
    val HEIGHT = 230.dp
    const val POSTER_RATIO = 2f / 3f
    const val WIDE_RATIO = 16f / 9f
    val SPACING = 12.dp
    val INFO_WIDTH = 620.dp
}

/**
 * Modern design card: a poster that opens into a wide image with the title logo while focused.
 * Width changes are layout, not a transform: D-pad focus search keeps working
 */
@Composable
fun ShelfCard(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerText: String? = null,
    height: Dp = Shelf.HEIGHT,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val animatorScale = rememberAnimatorScale()
    val width by animateDpAsState(
        targetValue = height * if (focused) Shelf.WIDE_RATIO else Shelf.POSTER_RATIO,
        animationSpec = tween(jvMillis(JvMotion.SHELF_MILLIS, animatorScale), easing = JvMotion.Easing),
        label = "shelf_width",
    )
    val wideAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(jvMillis(JvMotion.SHELF_MILLIS, animatorScale), easing = JvMotion.Easing),
        label = "shelf_wide_alpha",
    )

    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val heightPx = remember(height, density) { with(density) { height.roundToPx() } }
    val posterUrl =
        remember(item, heightPx) {
            item?.let {
                it.imageUrlOverride
                    ?: imageUrlService.getItemImageUrl(it, ImageType.PRIMARY, fillHeight = heightPx, useSeriesForPrimary = true)
            }
        }
    val wideUrl =
        remember(item) {
            item?.let {
                when {
                    it.backdropUrlOverride != null -> {
                        it.backdropUrlOverride
                    }

                    // Episodes show their own still, like a "continue watching" tile
                    it.type == BaseItemKind.EPISODE -> {
                        imageUrlService.getItemImageUrl(it, ImageType.PRIMARY, fillWidth = WIDE_FILL_WIDTH, useSeriesForPrimary = false)
                    }

                    else -> {
                        imageUrlService.getItemImageUrl(it, ImageType.BACKDROP, fillWidth = WIDE_FILL_WIDTH)
                    }
                }
            }
        }
    val logoUrl = rememberLogoUrl(item)
    var wideError by remember(wideUrl) { mutableStateOf(false) }
    var logoError by remember(logoUrl) { mutableStateOf(false) }
    // Load the wide image only once the card has been focused, not for every poster of the page
    var wideRequested by remember(item?.id) { mutableStateOf(false) }
    if (focused) wideRequested = true

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = source,
        modifier = modifier.size(width, height),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = JvCardDefaults.border(JvShapes.Poster),
        glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.5f), elevation = 12.dp)),
        shape = JvCardDefaults.shape(JvShapes.Poster),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (wideRequested && wideUrl != null && !wideError) {
                AsyncImage(
                    model = wideUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    onError = { wideError = true },
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = wideAlpha },
                )
            }
            if (wideAlpha > 0f) {
                // Bottom-left shade so the logo or the title stays readable on any image
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = wideAlpha }
                        .drawWithContent {
                            drawRect(
                                Brush.verticalGradient(
                                    0.45f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.75f),
                                ),
                            )
                        },
                )
                Box(
                    contentAlignment = Alignment.BottomStart,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 14.dp)
                            .fillMaxWidth(0.5f)
                            .height(height * 0.28f)
                            .graphicsLayer { alpha = wideAlpha },
                ) {
                    if (logoUrl != null && !logoError) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = item?.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            onError = { logoError = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = item?.title.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            ShelfBadges(item, cornerText, Modifier.align(Alignment.TopEnd))
            val playPercent = item?.data?.userData?.playedPercentage ?: 0.0
            if (playPercent > 0 && playPercent < 100) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(Cards.playedPercentHeight)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((playPercent / 100).toFloat())
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfBadges(
    item: BaseItem?,
    cornerText: String?,
    modifier: Modifier,
) {
    val playPercent = item?.data?.userData?.playedPercentage ?: 0.0
    val showSeen = item?.seen == true && (playPercent <= 0 || playPercent >= 100)
    if (!showSeen && cornerText == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(6.dp),
    ) {
        if (showSeen) {
            WatchedIcon(Modifier.size(24.dp))
        }
        if (cornerText != null) {
            Text(
                text = cornerText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .background(AppColors.TransparentBlack50, RoundedCornerShape(25))
                        .padding(4.dp),
            )
        }
    }
}

/** What the focused title is, under its row: details, then two lines of overview */
@Composable
fun ShelfInfo(
    item: BaseItem?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.width(Shelf.INFO_WIDTH).heightIn(min = SHELF_INFO_HEIGHT),
    ) {
        if (item == null) return@Column
        if (item.type == BaseItemKind.EPISODE && item.data.name.isNotNullOrBlank()) {
            EpisodeName(item.data.name!!)
        }
        QuickDetails(item.ui.quickDetails, item.timeRemainingOrRuntime)
        val overview = item.data.overview
        if (overview.isNotNullOrBlank()) {
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val WIDE_FILL_WIDTH = 960
private val SHELF_INFO_HEIGHT = 72.dp
