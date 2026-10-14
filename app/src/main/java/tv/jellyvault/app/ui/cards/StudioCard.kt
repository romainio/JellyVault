package tv.jellyvault.app.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import tv.jellyvault.app.ui.AspectRatios
import tv.jellyvault.app.ui.PreviewTvSpec
import tv.jellyvault.app.ui.components.Studio
import tv.jellyvault.app.ui.setup.rememberIdColor
import tv.jellyvault.app.ui.theme.JellyVaultTheme
import tv.jellyvault.app.ui.theme.jv.JvCardDefaults
import tv.jellyvault.app.ui.theme.jv.JvShapes
import java.util.UUID

@Composable
fun StudioCard(
    studio: Studio?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = StudioCard(
    studioId = studio?.id,
    name = studio?.name,
    imageUrl = studio?.imageUrl,
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
    interactionSource = interactionSource,
)

@Composable
fun StudioCard(
    studioId: UUID?,
    name: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val background = rememberIdColor(studioId).copy(alpha = .4f)
    var error by remember { mutableStateOf(false) }
    Card(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        scale = JvCardDefaults.scale(),
        border = JvCardDefaults.border(JvShapes.Landscape),
        glow = JvCardDefaults.glow(),
        shape = JvCardDefaults.shape(JvShapes.Landscape),
        colors =
            CardDefaults.colors(
                containerColor = Color.Transparent,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .aspectRatio(AspectRatios.WIDE)
                    .fillMaxSize()
                    .clip(JvShapes.Landscape),
        ) {
            if (imageUrl != null && !error) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                    contentScale = ContentScale.FillBounds,
                    contentDescription = null,
                    onError = {
                        error = true
                    },
                    modifier =
                        Modifier
                            .alpha(.75f)
                            .aspectRatio(AspectRatios.WIDE)
                            .fillMaxSize(),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .aspectRatio(AspectRatios.WIDE)
                            .fillMaxSize()
                            .background(background),
                ) {
                    Text(
                        text = name ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .padding(16.dp)
                                .align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun GenreCardPreview() {
    JellyVaultTheme {
        val studio =
            Studio(
                UUID.randomUUID(),
                "Adventure",
                null,
            )
        StudioCard(
            studio = studio,
            onClick = {},
            onLongClick = {},
            modifier = Modifier.width(180.dp),
        )
    }
}
