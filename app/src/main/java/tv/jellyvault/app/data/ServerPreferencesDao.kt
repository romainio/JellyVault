package tv.jellyvault.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tv.jellyvault.app.data.model.JellyfinUser
import tv.jellyvault.app.data.model.NavDrawerPinnedItem

@Dao
interface ServerPreferencesDao {
    fun getNavDrawerPinnedItems(user: JellyfinUser): List<NavDrawerPinnedItem> = getNavDrawerPinnedItems(user.rowId)

    @Query("SELECT * from NavDrawerPinnedItem WHERE userId=:userId")
    fun getNavDrawerPinnedItems(userId: Int): List<NavDrawerPinnedItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveNavDrawerPinnedItems(vararg items: NavDrawerPinnedItem)
}
