package tv.jellyvault.app.ui.detail.tmdb

sealed interface PlayLabel {
    data object Watch : PlayLabel

    data object Resume : PlayLabel

    data class WatchEpisode(
        val season: Int,
        val episode: Int,
    ) : PlayLabel

    data class ResumeEpisode(
        val season: Int,
        val episode: Int,
    ) : PlayLabel
}

/**
 * Whether the primary button should take focus: not while it is disabled waiting for Jellyfin (the « Actualiser » button is used then)
 */
fun shouldFocusPrimary(status: tv.jellyvault.app.catalog.arr.RequestStatus?): Boolean =
    status != tv.jellyvault.app.catalog.arr.RequestStatus.ReadySoon

/**
 * Label of the play button: « Regarder » / « Reprendre », with the episode for a series
 */
fun playLabel(playback: OwnedPlayback?): PlayLabel {
    playback ?: return PlayLabel.Watch
    val resume = playback.item.resumeMs > 0
    val plain = if (resume) PlayLabel.Resume else PlayLabel.Watch
    if (!playback.isEpisode) return plain
    val season = playback.item.data.parentIndexNumber ?: return plain
    val episode = playback.item.data.indexNumber ?: return plain
    return if (resume) PlayLabel.ResumeEpisode(season, episode) else PlayLabel.WatchEpisode(season, episode)
}
