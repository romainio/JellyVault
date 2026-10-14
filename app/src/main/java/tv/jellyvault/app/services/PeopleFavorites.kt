package tv.jellyvault.app.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import tv.jellyvault.app.data.model.BaseItem
import tv.jellyvault.app.data.model.Person
import tv.jellyvault.app.ui.letNotEmpty
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets people in media, specifically to check if they are favorited or not
 */
@Singleton
class PeopleFavorites
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
    ) {
        suspend fun getPeopleFor(item: BaseItem): List<Person> =
            item.data.people.orEmpty().map { it.id }.chunked(50).let { chunks ->
                val favorites =
                    chunks
                        .map {
                            api.itemsApi
                                .getItems(ids = it)
                                .content.items
                        }.flatten()
                        .associateBy({ it.id }, { it.userData?.isFavorite ?: false })
                val people =
                    item.data.people
                        ?.letNotEmpty { people ->
                            people.map {
                                Person.fromDto(
                                    context,
                                    it,
                                    favorites[it.id] ?: false,
                                    api,
                                )
                            }
                        }.orEmpty()
                people
            }
    }
