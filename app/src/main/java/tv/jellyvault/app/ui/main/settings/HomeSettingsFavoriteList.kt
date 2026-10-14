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
import org.jellyfin.sdk.model.api.BaseItemKind
import tv.jellyvault.app.R
import tv.jellyvault.app.ui.formatTypeName
import tv.jellyvault.app.ui.ifElse
import tv.jellyvault.app.ui.tryRequestFocus

@Composable
fun HomeSettingsFavoriteList(
    favoriteTypeOptions: List<BaseItemKind>,
    onClick: (BaseItemKind) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        TitleText(
            stringResource(R.string.add_row_for, stringResource(R.string.favorites)),
        )
        HomeSettingsLazyColumn(
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(favoriteTypeOptions) { index, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(formatTypeName(type)),
                    onClick = { onClick.invoke(type) },
                    modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
        }
    }
}
