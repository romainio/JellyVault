@file:UseSerializers(UUIDSerializer::class, ZonedDateTimeSerializer::class)

package tv.jellyvault.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import tv.jellyvault.app.data.ZonedDateTimeSerializer
import tv.jellyvault.app.preferences.SubtitleModePreference
import tv.jellyvault.app.preferences.UserProfileSettings
import tv.jellyvault.app.ui.isNotNullOrBlank
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Represents a Jellyfin server
 */
@Entity(tableName = "servers")
@Serializable
data class JellyfinServer(
    @PrimaryKey val id: UUID,
    val name: String?,
    val url: String,
    val version: String?,
    val lastUsed: ZonedDateTime? = null,
) {
    @get:Ignore
    val serverVersion: ServerVersion? by lazy { version?.let(ServerVersion::fromString) }
}

/**
 * Represents a Jellyfin user for a particular server
 */
@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(
            entity = JellyfinServer::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("serverId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("id", "serverId", unique = true)],
)
@Serializable
data class JellyfinUser(
    @PrimaryKey(autoGenerate = true)
    val rowId: Int = 0,
    @ColumnInfo(index = true)
    val id: UUID,
    val name: String?,
    @ColumnInfo(index = true)
    val serverId: UUID,
    val accessToken: String?,
    val pin: String? = null,
    @ColumnInfo(defaultValue = "false")
    val requireLogin: Boolean = false,
    val lastUsed: ZonedDateTime? = null,
    val uiLanguage: String? = null,
    @Embedded val appPreferences: JellyfinUserPreferences = JellyfinUserPreferences(),
) {
    val hasPin: Boolean get() = pin.isNotNullOrBlank()

    val isProtected: Boolean get() = hasPin || requireLogin

    override fun toString(): String =
        "JellyfinUser(rowId=$rowId, id=$id, name=$name, serverId=$serverId, lastUsed=$lastUsed, " +
            "accessToken?=${accessToken.isNotNullOrBlank()}, pin?=${pin.isNotNullOrBlank()}), " +
            "requireLogin=$requireLogin, lastUsed=$lastUsed, uiLanguage=$uiLanguage, " +
            "appPreferences=$appPreferences"
}

/**
 * Represents the relationship between [JellyfinServer] and its [JellyfinUser]
 */
data class JellyfinServerUsers(
    @Embedded val server: JellyfinServer,
    @Relation(
        parentColumn = "id",
        entityColumn = "serverId",
    )
    val users: List<JellyfinUser>,
)

@Serializable
data class JellyfinUserPreferences(
    @ColumnInfo(defaultValue = "")
    val preferredAudioLanguage: String = UserProfileSettings.USE_USER_PROFILE,
    @ColumnInfo(defaultValue = "")
    val preferredSubtitleLanguage: String = UserProfileSettings.USE_USER_PROFILE,
    @ColumnInfo(defaultValue = "USE_USER_PROFILE")
    val subtitleMode: SubtitleModePreference = SubtitleModePreference.USE_USER_PROFILE,
)
