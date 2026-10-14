package tv.jellyvault.app.ui.main.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import tv.jellyvault.app.R
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.catalogRowTitle
import tv.jellyvault.app.catalog.personalRows
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.ui.ifElse
import tv.jellyvault.app.ui.tryRequestFocus

@Composable
fun HomeSettingsTmdbList(
    onClick: (HomeRowConfig) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    val options =
        remember {
            listOf(
                HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE),
                HomeRowConfig.TmdbTrending(TmdbMediaType.TV),
            ) + personalRows() + HomeRowConfig.TmdbWatchlist() + HomeRowConfig.TmdbShelves() +
                CatalogGenre.entries.map { HomeRowConfig.TmdbGenre(it) }
        }
    Column(modifier = modifier) {
        TitleText(stringResource(R.string.add_row_for, stringResource(R.string.catalog_tmdb_rows)))
        HomeSettingsLazyColumn(
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(options) { index, config ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = catalogRowTitle(config)!!.getString(),
                    onClick = { onClick.invoke(config) },
                    modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
        }
    }
}
