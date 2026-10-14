package tv.jellyvault.app.ui.detail.tmdb

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jellyvault.app.data.model.BaseItem
import java.util.UUID

class TmdbPlayLabelTest {
    private fun data(ticks: Long) =
        UserItemDataDto(
            playbackPositionTicks = ticks,
            playCount = 0,
            isFavorite = false,
            lastPlayedDate = null,
            played = false,
            key = "",
            itemId = UUID.randomUUID(),
        )

    private fun movie(ticks: Long) =
        OwnedPlayback(BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE, userData = data(ticks))), null)

    private fun episode(
        ticks: Long,
        season: Int,
        number: Int,
    ) = OwnedPlayback(
        BaseItem(
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.EPISODE,
                parentIndexNumber = season,
                indexNumber = number,
                userData = data(ticks),
            ),
        ),
        null,
    )

    @Test
    fun movies() {
        assertEquals(PlayLabel.Watch, playLabel(movie(0)))
        assertEquals(PlayLabel.Resume, playLabel(movie(600_000_000L)))
        assertEquals(PlayLabel.Watch, playLabel(null))
    }

    @Test
    fun episodes() {
        assertEquals(PlayLabel.WatchEpisode(1, 1), playLabel(episode(0, 1, 1)))
        assertEquals(PlayLabel.ResumeEpisode(2, 4), playLabel(episode(600_000_000L, 2, 4)))
    }
}
