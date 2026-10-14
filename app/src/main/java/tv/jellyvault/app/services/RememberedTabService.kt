package tv.jellyvault.app.services

import kotlinx.coroutines.withContext
import tv.jellyvault.app.data.RememberedTabDao
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.data.model.RememberedTab
import tv.jellyvault.app.util.JvDispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RememberedTabService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val rememberedTabDao: RememberedTabDao,
    ) {
        suspend fun getRememberedTab(itemId: String): Int? =
            withContext(JvDispatchers.IO) {
                serverRepository.currentUser?.rowId?.let { userId ->
                    rememberedTabDao.getRememberedTab(userId, itemId)?.index
                }
            }

        suspend fun saveRememberedTab(
            itemId: String,
            tabIndex: Int,
        ): Unit =
            withContext(JvDispatchers.IO) {
                serverRepository.currentUser?.rowId?.let { userId ->
                    rememberedTabDao.save(RememberedTab(userId, itemId, tabIndex))
                }
            }
    }
