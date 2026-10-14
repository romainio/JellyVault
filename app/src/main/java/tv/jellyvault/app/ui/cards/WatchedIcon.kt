package tv.jellyvault.app.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import tv.jellyvault.app.ui.PreviewTvSpec
import tv.jellyvault.app.ui.theme.JellyVaultTheme

@Composable
fun WatchedIcon(
    modifier: Modifier = Modifier,
    padding: Dp = 2.dp,
) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = WatchedIconColor(),
        modifier =
            modifier
                .background(WatchedIconBackground(), shape = CircleShape)
                .border(.5.dp, Color.Black, CircleShape)
                .padding(padding),
    )
}

/** Accent disc with a dark check: a white check on the near-white border color was invisible */
@Composable
fun WatchedIconBackground(): Color = MaterialTheme.colorScheme.primary

@Composable
fun WatchedIconColor(): Color = MaterialTheme.colorScheme.onPrimary

@PreviewTvSpec
@Composable
private fun WatchedIconPreview() {
    JellyVaultTheme {
        WatchedIcon(Modifier.size(64.dp))
    }
}
