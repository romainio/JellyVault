package tv.jellyvault.app.ui.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.jellyfin.sdk.model.api.ImageType
import tv.jellyvault.app.R
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.ui.AppColors
import tv.jellyvault.app.ui.Cards
import tv.jellyvault.app.ui.FontAwesome
import tv.jellyvault.app.ui.LocalImageUrlService
import tv.jellyvault.app.ui.gt
import tv.jellyvault.app.ui.isNotNullOrBlank
import tv.jellyvault.app.ui.logCoilError

/**
 * Display an image for an item with optional overlay data
 *
 * This will fetch the image using fillWidth/fillHeight based on the layout size
 */
@Composable
fun ItemCardImage(
    item: BaseItem?,
    name: String?,
    showOverlay: Boolean,
    favorite: Boolean,
    watched: Boolean,
    unwatchedCount: Int,
    watchedPercent: Double?,
    numberOfVersions: Int,
    modifier: Modifier = Modifier,
    /** In the library and not seen: a small check in the corner */
    inLibrary: Boolean = false,
    imageType: ImageType = ImageType.PRIMARY,
    useFallbackText: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    fillWidth: Int? = null,
    fillHeight: Int? = null,
) {
    val imageUrlService = LocalImageUrlService.current
    val imageUrl =
        remember(item, imageType, fillWidth, fillHeight) {
            // TMDB-only titles carry their own image and must never be looked up on the Jellyfin server
            if (item?.imageUrlOverride != null) {
                item.imageUrlOverride
            } else if (item != null && (fillWidth.gt(0) || fillHeight.gt(0))) {
                imageUrlService.getItemImageUrl(
                    item,
                    imageType,
                    fillWidth = fillWidth,
                    fillHeight = fillHeight,
                )
            } else {
                null
            }
        }
    ItemCardImage(
        imageUrl = imageUrl,
        name = name,
        showOverlay = showOverlay,
        favorite = favorite,
        watched = watched,
        unwatchedCount = unwatchedCount,
        watchedPercent = watchedPercent,
        numberOfVersions = numberOfVersions,
        modifier = modifier,
        inLibrary = inLibrary,
        useFallbackText = useFallbackText,
        contentScale = contentScale,
    )
}

@Composable
fun ItemCardImage(
    imageUrl: String?,
    name: String?,
    showOverlay: Boolean,
    favorite: Boolean,
    watched: Boolean,
    unwatchedCount: Int,
    watchedPercent: Double?,
    numberOfVersions: Int,
    modifier: Modifier = Modifier,
    inLibrary: Boolean = false,
    useFallbackText: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    fallback: @Composable BoxScope.() -> Unit = {
        ItemCardImageFallback(
            name = name,
            useFallbackText = useFallbackText,
            modifier = Modifier,
        )
    },
) {
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier,
    ) {
        if (!imageError && imageUrl.isNotNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = contentScale,
                alignment = Alignment.Center,
                onError = {
                    logCoilError(imageUrl, it.result)
                    imageError = true
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
            )
        } else {
            fallback.invoke(this)
        }
        if (showOverlay) {
            ItemCardImageOverlay(
                favorite = favorite,
                watched = watched,
                unwatchedCount = unwatchedCount,
                watchedPercent = watchedPercent,
                numberOfVersions = numberOfVersions,
                inLibrary = inLibrary,
                modifier = Modifier,
            )
        }
    }
}

@Composable
fun BoxScope.ItemCardImageFallback(
    name: String?,
    useFallbackText: Boolean,
    modifier: Modifier = Modifier,
) {
    // TODO options for overriding fallback
    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .fillMaxSize()
                .align(Alignment.TopCenter),
    ) {
        if (useFallbackText && name.isNotNullOrBlank()) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier =
                    Modifier
                        .padding(8.dp)
                        .align(Alignment.Center),
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.video_solid),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter =
                    ColorFilter.tint(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        BlendMode.SrcIn,
                    ),
                modifier =
                    Modifier
                        .fillMaxSize(.4f)
                        .align(Alignment.Center),
            )
        }
    }
}

@Composable
fun ItemCardImageOverlay(
    favorite: Boolean,
    watched: Boolean,
    unwatchedCount: Int,
    watchedPercent: Double?,
    numberOfVersions: Int,
    modifier: Modifier = Modifier,
    inLibrary: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
        ) {
            if (numberOfVersions > 1) {
                Box(
                    modifier =
                        Modifier
                            .background(
                                AppColors.TransparentBlack50,
                                shape = RoundedCornerShape(25),
                            ),
                ) {
                    Text(
                        text = numberOfVersions.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
//                            fontSize = 16.sp,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            if (favorite) {
                FavoriteIndicator()
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
        ) {
            if (watched && (watchedPercent == null || watchedPercent <= 0.0 || watchedPercent >= 100.0)) {
                WatchedIcon(Modifier.size(24.dp))
            }
            if (inLibrary) {
                CornerText("✓")
            }
            if (unwatchedCount > 0) {
                Box(
                    modifier =
                        Modifier
                            .background(
                                AppColors.TransparentBlack50,
                                shape = RoundedCornerShape(25),
                            ),
                ) {
                    Text(
                        text = unwatchedCount.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
//                            fontSize = 16.sp,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }

        if (watchedPercent != null && watchedPercent > 0 && watchedPercent < 100) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .background(
                            MaterialTheme.colorScheme.tertiary,
                        ).clip(RectangleShape)
                        .height(Cards.playedPercentHeight)
                        .fillMaxWidth((watchedPercent / 100.0).toFloat()),
            )
        }
    }
}

@Composable
fun FavoriteIndicator(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
) = Text(
    color = colorResource(android.R.color.holo_red_light),
    text = stringResource(R.string.fa_heart),
    fontSize = fontSize,
    fontFamily = FontAwesome,
    modifier = modifier,
)

/**
 * Small text in a translucent box, as on the home cards (library check)
 */
@Composable
private fun CornerText(text: String) =
    Box(
        modifier =
            Modifier
                .background(
                    AppColors.TransparentBlack50,
                    shape = RoundedCornerShape(25),
                ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(4.dp),
        )
    }
