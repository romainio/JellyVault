package tv.jellyvault.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.jellyvault.app.data.model.JellyfinUser
import tv.jellyvault.app.data.model.LibraryDisplayInfo
import tv.jellyvault.app.ui.toServerString
import java.util.UUID

@Dao
interface LibraryDisplayInfoDao {
    fun getItem(
        user: JellyfinUser,
        itemId: UUID,
    ): LibraryDisplayInfo? = getItem(user.rowId, itemId.toServerString())

    fun getItem(
        user: JellyfinUser,
        itemId: String,
    ): LibraryDisplayInfo? = getItem(user.rowId, itemId)

    @Query("SELECT * from LibraryDisplayInfo WHERE userId=:userId AND itemId=:itemId")
    fun getItem(
        userId: Int,
        itemId: String,
    ): LibraryDisplayInfo?

    @Query("SELECT * from LibraryDisplayInfo WHERE userId=:userId AND itemId=:itemId")
    fun getItemAsFlow(
        userId: Int,
        itemId: String,
    ): Flow<LibraryDisplayInfo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveItem(item: LibraryDisplayInfo): Long

    @Query("SELECT * from LibraryDisplayInfo WHERE userId=:userId")
    fun getItems(userId: Int): List<LibraryDisplayInfo>
}
