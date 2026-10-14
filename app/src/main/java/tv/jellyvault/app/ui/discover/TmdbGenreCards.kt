package tv.jellyvault.app.ui.discover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.ui.cards.GenreCard
import tv.jellyvault.app.ui.detail.CardGrid
import tv.jellyvault.app.ui.detail.CardGridItem
import java.util.UUID

/**
 * A TMDB genre shown as a card in the genres tab
 */
data class TmdbGenreItem(
    val genre: CatalogGenre,
    val name: String,
) : CardGridItem {
    override val gridId: String get() = genre.name
    override val playable: Boolean get() = false
    override val sortName: String get() = name
}

/**
 * Genres tab of the Films / Séries pages: every TMDB genre available for [mediaType]
 */
@Composable
fun TmdbGenreCards(
    mediaType: TmdbMediaType,
    tmdbConfigured: Boolean,
    onClick: (CatalogGenre) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!tmdbConfigured) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.catalog_tmdb_not_configured),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    val context = LocalContext.current
    val genres =
        remember(mediaType) {
            CatalogGenre.entries
                .filter { mediaType == TmdbMediaType.MOVIE || it.tvGenreId != null }
                .let { genres -> if (mediaType == TmdbMediaType.MOVIE) genres else genres.distinctBy { it.tvGenreId } }
                .map { TmdbGenreItem(it, context.getString(it.titleRes)) }
        }
    val gridFocusRequester = remember { FocusRequester() }
    CardGrid(
        pager = genres,
        onClickItem = { _, item -> onClick(item.genre) },
        onLongClickItem = { _, _ -> },
        onClickPlay = { _, _ -> },
        letterPosition = { 0 },
        gridFocusRequester = gridFocusRequester,
        showJumpButtons = false,
        showLetterButtons = false,
        modifier = modifier.fillMaxSize(),
        columns = 4,
        cardContent = { (item, _, onClick, onLongClick, _, mod) ->
            GenreCard(
                // Stable id per genre so each card gets its own color
                genreId = item?.let { UUID.nameUUIDFromBytes("tmdb-genre:${it.genre.name}".toByteArray()) },
                name = item?.name,
                imageUrl = null,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = mod,
            )
        },
    )
}
