package tv.jellyvault.app.ui.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import tv.jellyvault.app.data.model.Person
import tv.jellyvault.app.ui.cards.PersonRow
import tv.jellyvault.app.ui.theme.jv.JvShapes

/**
 * Cast of a Jellyfin title, opened from the « … » menu (the page itself no longer shows it)
 */
@Composable
fun CastDialog(
    people: List<Person>,
    onClickPerson: (Person) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = JvShapes.Dialog,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.width(860.dp),
        ) {
            PersonRow(people = people, onClick = onClickPerson, modifier = Modifier.padding(16.dp))
        }
    }
}
