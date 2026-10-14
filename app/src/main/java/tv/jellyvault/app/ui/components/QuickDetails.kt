package tv.jellyvault.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.jellyfin.sdk.model.DateTime
import tv.jellyvault.app.R
import tv.jellyvault.app.data.model.QuickDetailsData
import tv.jellyvault.app.preferences.DisplayToggle
import tv.jellyvault.app.ui.dot
import tv.jellyvault.app.ui.formatTime
import tv.jellyvault.app.ui.util.LocalClock
import tv.jellyvault.app.ui.util.LocalInterfaceCustomization
import tv.jellyvault.app.ui.visibleQuickDetailSegments
import kotlin.time.Duration

@Composable
fun QuickDetails(
    details: QuickDetailsData?,
    timeRemaining: Duration?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    endsAt: DateTime? = null,
) {
    val enabled = LocalInterfaceCustomization.current.enabledDisplayToggles
    val inlineContentMap = rememberQuickDetailsContentMap(textStyle)
    // Only separators between visible segments: no leading "•" when the basic details are empty
    val segments =
        remember(details, enabled) {
            if (details == null) {
                emptyList()
            } else {
                visibleQuickDetailSegments(
                    listOf(
                        details.basic,
                        details.officialRating.takeIf { DisplayToggle.OFFICIAL_RATING in enabled },
                        details.communityRating.takeIf { DisplayToggle.COMMUNITY_RATING in enabled },
                        details.criticRating.takeIf { DisplayToggle.CRITIC_RATING in enabled },
                    ),
                )
            }
        }
    Row(modifier = modifier) {
        segments.forEach { QuickDetailsText(it, Modifier, textStyle, inlineContentMap) }
        if (timeRemaining != null) {
            TimeRemaining(timeRemaining, textStyle = textStyle, showDot = segments.isNotEmpty())
        } else if (endsAt != null) {
            EndsAt(endsAt, textStyle = textStyle, showDot = segments.isNotEmpty())
        }
    }
}

@NonRestartableComposable
@Composable
fun QuickDetailsText(
    str: AnnotatedString?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    inlineContentMap: Map<String, InlineTextContent> = rememberQuickDetailsContentMap(textStyle),
) = str?.let {
    Text(
        text = str,
        color = MaterialTheme.colorScheme.onSurface,
        style = textStyle,
        inlineContent = inlineContentMap,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun rememberQuickDetailsContentMap(textStyle: TextStyle = MaterialTheme.typography.titleSmall) =
    remember(textStyle) {
        mapOf(
            "star" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        tint = FilledStarColor,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            "rotten" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotten_tomatoes_rotten),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified,
                    )
                },
            "fresh" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotten_tomatoes_fresh),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified,
                    )
                },
        )
    }

@Composable
fun TimeRemaining(
    remaining: Duration,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    showDot: Boolean = true,
) {
    val now by LocalClock.current.now
    EndsAt(
        endsAt = now.plusSeconds(remaining.inWholeSeconds),
        modifier = modifier,
        textStyle = textStyle,
        showDot = showDot,
    )
}

@Composable
fun EndsAt(
    endsAt: DateTime,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    showDot: Boolean = true,
) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val remainingStr =
        remember(endsAt, resources, context, showDot) {
            val endTimeStr = formatTime(context, endsAt)
            buildAnnotatedString {
                if (showDot) dot()
                append(resources.getString(R.string.ends_at, endTimeStr))
            }
        }
    Text(
        text = remainingStr,
        style = textStyle,
        maxLines = 1,
        modifier = modifier,
    )
}
