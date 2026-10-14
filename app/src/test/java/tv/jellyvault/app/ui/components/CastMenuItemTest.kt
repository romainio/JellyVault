package tv.jellyvault.app.ui.components

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jellyvault.app.data.model.BaseItem
import java.util.UUID

class CastMenuItemTest {
    private val item = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))

    private fun actions(onShowCast: ((BaseItem) -> Unit)?) =
        ContextMenuActions(
            navigateTo = {},
            onShowOverview = {},
            onClickWatch = { _, _ -> },
            onClickFavorite = { _, _ -> },
            onClickAddPlaylist = {},
            onSendMediaInfo = {},
            onDeleteItem = {},
            onChooseVersion = { _, _ -> },
            onChooseTracks = {},
            onClearChosenStreams = {},
            onShowCast = onShowCast,
        )

    @Test
    fun cast_entry_only_when_the_page_can_show_it() {
        assertNotNull(castMenuItem("Casting", actions { }, item))
        assertNull(castMenuItem("Casting", actions(null), item))
    }

    @Test
    fun cast_entry_is_only_for_the_page_title_not_for_long_pressed_items() {
        val other = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))
        val pageActions = actions { }.copy(castForItemId = item.id)
        assertNotNull(castMenuItem("Casting", pageActions, item))
        assertNull(castMenuItem("Casting", pageActions, other))
    }
}
