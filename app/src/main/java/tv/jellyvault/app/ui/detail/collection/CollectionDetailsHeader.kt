package tv.jellyvault.app.ui.detail.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.ui.components.GenreText
import tv.jellyvault.app.ui.components.HeaderUtils
import tv.jellyvault.app.ui.components.OverviewText
import tv.jellyvault.app.ui.components.QuickDetails
import tv.jellyvault.app.ui.components.TitleOrLogo
import tv.jellyvault.app.ui.letNotEmpty
import tv.jellyvault.app.util.ExceptionHandler

@Composable
fun CollectionDetailsHeader(
    collection: BaseItem,
    showLogo: Boolean,
    logoImageUrl: String?,
    overviewOnClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier = Modifier,
) {
    val dto = collection.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            title = collection.name,
            showLogo = showLogo,
            logoImageUrl = logoImageUrl,
            modifier =
                Modifier
                    .fillMaxWidth(.75f)
                    .padding(start = HeaderUtils.startPadding),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            QuickDetails(
                collection.ui.quickDetails,
                collection.timeRemainingOrRuntime,
                Modifier.padding(start = HeaderUtils.startPadding),
            )

            dto.genres?.letNotEmpty {
                GenreText(it, Modifier.padding(start = HeaderUtils.startPadding))
            }
            dto.taglines?.firstOrNull()?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                )
            }

            // Description
            dto.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }
        }
    }
}
