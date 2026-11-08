@file:UseSerializers(UUIDSerializer::class)

package tv.jellyvault.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/**
 * Reserved table (database version 37): not read nor written yet.
 *
 * Stores the user's chosen track for a media item. This is by type, ie Audio vs Subtitle tracks
 *
 * Additional information about the chosen track is stored to compare against tracks in other media
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = arrayOf("rowId"),
            childColumns = arrayOf("userId"),
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    primaryKeys = ["userId", "parentId", "type"],
)
@Serializable
data class SeriesTrackChoice(
    val userId: Int,
    /**
     * Season or Series
     */
    val parentId: UUID,
    /**
     * Type of track, ie audio or subtitle
     */
    val type: SeriesTrackChoiceType,
    /**
     * Origin of the [parentId]
     */
    val parentType: TrackChoiceParentType = TrackChoiceParentType.UNKNOWN,
    val itemId: UUID? = null,
    val language: String? = null,
    val activation: ActivationFlag = ActivationFlag.ACTIVATED,
    /**
     * Flags for the track such as external or forced
     *
     */
    val trackFlags: Int = 0,
    val codec: String? = null,
    /**
     * The track index based on its position within all streams of the same type
     */
    val relativeTrackIndex: Int? = null,
    val title: String? = null,
    val audioChannels: Int? = null,
)

enum class SeriesTrackChoiceType(
    val mediaStreamType: MediaStreamType,
) {
    AUDIO(MediaStreamType.AUDIO),
    SUBTITLE(MediaStreamType.SUBTITLE),
}

/**
 * How was the track activated
 */
enum class ActivationFlag {
    ACTIVATED,
    DISABLED,
    ONLY_FORCED,
}

/**
 * Where the [SeriesTrackChoice.parentId] comes from
 */
enum class TrackChoiceParentType {
    /**
     * Parent ID is from the series
     */
    SERIES,

    /**
     * Parent ID is from the season
     */
    SEASON,

    /**
     * Parent ID is from the deprecated [PlaybackLanguageChoice]
     */
    DEPRECATED,

    /**
     * Parent ID origin is unknown
     */
    UNKNOWN,
}
