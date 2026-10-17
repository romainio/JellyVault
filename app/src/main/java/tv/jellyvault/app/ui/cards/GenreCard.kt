package tv.jellyvault.app.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
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
import tv.jellyvault.app.ui.components.Genre
import tv.jellyvault.app.ui.setup.rememberIdColor
import tv.jellyvault.app.ui.theme.JellyVaultTheme
import tv.jellyvault.app.ui.theme.jv.JvCardDefaults
import tv.jellyvault.app.ui.theme.jv.JvShapes
import java.util.UUID

@Composable
fun GenreCard(
    genre: Genre?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = GenreCard(
    genreId = genre?.id,
    name = genre?.name,
    imageUrl = genre?.imageUrl,
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
    interactionSource = interactionSource,
)

@Composable
fun GenreCard(
    genreId: UUID?,
    name: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val background = rememberIdColor(genreId).copy(alpha = .6f)
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
            if (imageUrl != null) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .aspectRatio(AspectRatios.WIDE)
                            .fillMaxSize(),
                )
            }
            Box(
                modifier =
                    Modifier
                        .aspectRatio(AspectRatios.WIDE)
                        .fillMaxSize()
                        // A picture keeps its colors under a dark veil that keeps the name readable
                        .background(if (imageUrl != null) IMAGE_VEIL else SolidColor(background)),
            ) {
                Text(
                    text = name ?: "",
                    color = if (imageUrl != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            shadow = if (imageUrl != null) Shadow(Color.Black, blurRadius = 8f) else null,
                        ),
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

private val IMAGE_VEIL = Brush.verticalGradient(listOf(Color.Black.copy(alpha = .25f), Color.Black.copy(alpha = .65f)))

@PreviewTvSpec
@Composable
private fun GenreCardPreview() {
    JellyVaultTheme {
        val genre =
            Genre(
                UUID.randomUUID(),
                "Adventure",
                null,
            )
        GenreCard(
            genre = genre,
            onClick = {},
            onLongClick = {},
            modifier = Modifier.width(180.dp),
        )
    }
}
