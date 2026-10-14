package tv.jellyvault.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tv.jellyvault.app.data.model.ItemPlayback
import tv.jellyvault.app.data.model.ItemTrackModification
import tv.jellyvault.app.data.model.JellyfinUser
import java.util.UUID

@Dao
interface ItemPlaybackDao {
    suspend fun getItem(
        user: JellyfinUser,
        itemId: UUID,
    ): ItemPlayback? = getItem(user.rowId, itemId)

    @Query("SELECT * from ItemPlayback WHERE userId=:userId AND itemId=:itemId")
    suspend fun getItem(
        userId: Int,
        itemId: UUID,
    ): ItemPlayback?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveItem(item: ItemPlayback): Long

    @Delete
    suspend fun deleteItem(item: ItemPlayback)

    @Query("SELECT * from ItemPlayback WHERE userId=:userId")
    suspend fun getItems(userId: Int): List<ItemPlayback>

    @Query("SELECT * FROM ItemTrackModification WHERE userId=:userId AND itemId=:itemId")
    suspend fun getTrackModifications(
        userId: Int,
        itemId: UUID,
    ): List<ItemTrackModification>

    @Query("SELECT * FROM ItemTrackModification WHERE userId=:userId AND itemId=:itemId AND trackIndex=:trackIndex")
    suspend fun getTrackModifications(
        userId: Int,
        itemId: UUID,
        trackIndex: Int,
    ): ItemTrackModification?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveItem(item: ItemTrackModification): Long
}
