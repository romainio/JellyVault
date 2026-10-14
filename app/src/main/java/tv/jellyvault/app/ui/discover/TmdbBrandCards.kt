package tv.jellyvault.app.ui.discover

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.ui.AspectRatios
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.theme.jv.JvCardDefaults
import tv.jellyvault.app.ui.theme.jv.JvFocus
import tv.jellyvault.app.ui.theme.jv.JvShapes

/** Many TMDB logos are dark on transparent: show them on a light card */
private val LOGO_BACKGROUND = Color(0xFFE9E6EE)
private val LOGO_TEXT = Color(0xFF1B1726)

/**
 * Studios & sagas (movies) or Networks (series): cards that open a browse page
 */
@Composable
fun TmdbBrandCards(
    mediaType: TmdbMediaType,
    modifier: Modifier = Modifier,
    viewModel: BrandCardsViewModel = hiltViewModel(key = "brands_${mediaType.name}"),
) {
    LaunchedEffect(mediaType) { viewModel.init(mediaType) }
    val studios by viewModel.studios.collectAsState()
    val sagas by viewModel.sagas.collectAsState()
    val onClick = { card: BrandCard -> viewModel.navigationManager.navigateTo(Destination.TmdbBrowse(card.target)) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(if (mediaType == TmdbMediaType.MOVIE) R.string.discover_studios else R.string.discover_networks)
        }
        items(studios, key = { it.target.toString() }) { BrandCardView(it, onClick) }
        if (sagas.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(R.string.discover_sagas) }
            items(sagas, key = { it.target.toString() }) { BrandCardView(it, onClick) }
        }
    }
}

@Composable
private fun SectionTitle(
    @StringRes res: Int,
) {
    Text(stringResource(res), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun BrandCardView(
    card: BrandCard,
    onClick: (BrandCard) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = { onClick(card) },
        interactionSource = source,
        scale = JvCardDefaults.scale(),
        // The white ring does not show on the light logo card
        border =
            CardDefaults.border(
                focusedBorder =
                    Border(
                        BorderStroke(
                            JvFocus.BORDER,
                            if (card.isLogo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border,
                        ),
                        shape = JvShapes.Landscape,
                    ),
            ),
        glow = JvCardDefaults.glow(),
        shape = JvCardDefaults.shape(JvShapes.Landscape),
        modifier = Modifier.fillMaxWidth().aspectRatio(AspectRatios.WIDE),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(if (card.isLogo) LOGO_BACKGROUND else MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // The name stays until the image is actually drawn (slow network, missing file)
            var imageShown by remember(card.imageUrl) { mutableStateOf(false) }
            if (card.imageUrl != null) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.name,
                    contentScale = if (card.isLogo) ContentScale.Fit else ContentScale.Crop,
                    onSuccess = { imageShown = true },
                    onError = { imageShown = false },
                    modifier = Modifier.fillMaxSize().padding(if (card.isLogo) 24.dp else 0.dp),
                )
            }
            if (!card.isLogo) {
                // Keeps the saga name readable on bright backdrops
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))),
                )
            }
            if (!imageShown || !card.isLogo) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (card.isLogo) LOGO_TEXT else Color.White,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .align(if (card.isLogo) Alignment.Center else Alignment.BottomStart)
                            .padding(12.dp),
                )
            }
        }
    }
}
