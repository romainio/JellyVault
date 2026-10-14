package tv.jellyvault.app.catalog.library

import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import tv.jellyvault.app.ui.HomeItemFields
import java.util.UUID
import javax.inject.Inject

data class ProviderIdPage(
    val items: List<BaseItemDto>,
    val totalRecordCount: Int,
)

/**
 * Access to the Jellyfin library needed by the catalog
 */
interface LibraryItemSource {
    /**
     * A page of all items of [kind] in the user's libraries, with only their provider IDs
     */
    suspend fun fetchProviderIdPage(
        kind: BaseItemKind,
        startIndex: Int,
        limit: Int,
    ): ProviderIdPage

    /**
     * Full items (as displayed on the home page) for the given IDs
     */
    suspend fun fetchItems(ids: List<UUID>): List<BaseItemDto>

    /**
     * Ask Jellyfin to scan its libraries (needs an administrator); false when it refused or failed
     */
    suspend fun requestLibraryScan(): Boolean
}

class JellyfinLibraryItemSource
    @Inject
    constructor(
        private val api: ApiClient,
    ) : LibraryItemSource {
        override suspend fun fetchProviderIdPage(
            kind: BaseItemKind,
            startIndex: Int,
            limit: Int,
        ): ProviderIdPage {
            val result =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            recursive = true,
                            includeItemTypes = listOf(kind),
                            fields = listOf(ItemFields.PROVIDER_IDS),
                            startIndex = startIndex,
                            limit = limit,
                            enableImages = false,
                            enableUserData = false,
                            enableTotalRecordCount = true,
                        ),
                    ).content
            return ProviderIdPage(result.items, result.totalRecordCount)
        }

        override suspend fun requestLibraryScan(): Boolean =
            try {
                api.libraryApi.refreshLibrary()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Jellyfin did not accept the library scan")
                false
            }

        override suspend fun fetchItems(ids: List<UUID>): List<BaseItemDto> {
            if (ids.isEmpty()) return emptyList()
            return api.itemsApi
                .getItems(GetItemsRequest(ids = ids, fields = HomeItemFields))
                .content
                .items
        }
    }
