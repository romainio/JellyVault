package tv.jellyvault.app.test

import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.junit.Assert
import org.junit.Test
import tv.jellyvault.app.catalog.CatalogGenre
import tv.jellyvault.app.catalog.CatalogMediaType
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.model.HomeRowConfig
import tv.jellyvault.app.data.model.HomeRowViewOptions
import tv.jellyvault.app.preferences.PrefContentScale
import tv.jellyvault.app.services.HomeSettingsService
import tv.jellyvault.app.ui.AspectRatio
import tv.jellyvault.app.ui.components.ViewOptionImageType
import tv.jellyvault.app.ui.data.SortAndDirection
import kotlin.reflect.KClass

class TestHomeRowSamples {
    companion object {
        val SAMPLES =
            listOf(
                HomeRowConfig.RecentlyAdded(
                    parentId = UUID.randomUUID(),
                    viewOptions =
                        HomeRowViewOptions(
                            heightDp = 100,
                            spacing = 8,
                            contentScale = PrefContentScale.CROP,
                            aspectRatio = AspectRatio.FOUR_THREE,
                            imageType = ViewOptionImageType.THUMB,
                            showTitles = false,
                            useSeries = false,
                        ),
                ),
                HomeRowConfig.RecentlyReleased(
                    parentId = UUID.randomUUID(),
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.Genres(
                    parentId = UUID.randomUUID(),
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.Studios(parentId = UUID.randomUUID()),
                HomeRowConfig.ContinueWatching(
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.NextUp(
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.ContinueWatchingCombined(
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.ByParent(
                    parentId = UUID.randomUUID(),
                    recursive = true,
                    sort = SortAndDirection(ItemSortBy.CRITIC_RATING, SortOrder.ASCENDING),
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.GetItems(
                    name = "Episodes by date created",
                    getItems =
                        GetItemsRequest(
                            parentId = UUID.randomUUID(),
                            recursive = true,
                            isFavorite = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                        ),
                    viewOptions = HomeRowViewOptions(),
                ),
                HomeRowConfig.Favorite(kind = BaseItemKind.SERIES),
                HomeRowConfig.Recordings(),
                HomeRowConfig.TvPrograms(),
                HomeRowConfig.TvChannels(),
                HomeRowConfig.Suggestions(parentId = UUID.randomUUID()),
                HomeRowConfig.TmdbTrending(TmdbMediaType.MOVIE),
                HomeRowConfig.TmdbGenre(CatalogGenre.ACTION, CatalogMediaType.BOTH),
                HomeRowConfig.TmdbForYou(TmdbMediaType.MOVIE),
                HomeRowConfig.TmdbBecause(TmdbMediaType.TV, 1),
                HomeRowConfig.TmdbShelves(),
                HomeRowConfig.TmdbDirectors(),
                HomeRowConfig.TmdbWatchlist(),
                HomeRowConfig.TmdbShelf("fr_films"),
            )
    }

    @Test
    fun `Check all types have a sample`() {
        // This ensures there is a sample for each possible HomeRowConfig type
        val foundTypes = mutableSetOf<KClass<out HomeRowConfig>>()
        SAMPLES.forEach {
            when (it) {
                is HomeRowConfig.ContinueWatching -> foundTypes.add(it::class)
                is HomeRowConfig.ContinueWatchingCombined -> foundTypes.add(it::class)
                is HomeRowConfig.Genres -> foundTypes.add(it::class)
                is HomeRowConfig.NextUp -> foundTypes.add(it::class)
                is HomeRowConfig.RecentlyAdded -> foundTypes.add(it::class)
                is HomeRowConfig.RecentlyReleased -> foundTypes.add(it::class)
                is HomeRowConfig.ByParent -> foundTypes.add(it::class)
                is HomeRowConfig.GetItems -> foundTypes.add(it::class)
                is HomeRowConfig.Favorite -> foundTypes.add(it::class)
                is HomeRowConfig.Recordings -> foundTypes.add(it::class)
                is HomeRowConfig.TvPrograms -> foundTypes.add(it::class)
                is HomeRowConfig.Suggestions -> foundTypes.add(it::class)
                is HomeRowConfig.TvChannels -> foundTypes.add(it::class)
                is HomeRowConfig.Studios -> foundTypes.add(it::class)
                is HomeRowConfig.TmdbTrending -> foundTypes.add(it::class)
                is HomeRowConfig.TmdbGenre -> foundTypes.add(it::class)
                is HomeRowConfig.TmdbForYou -> foundTypes.add(it::class)
                is HomeRowConfig.TmdbBecause -> foundTypes.add(it::class)
                is HomeRowConfig.TmdbDirectors -> foundTypes.add(it::class)
                is HomeRowConfig.TmdbShelves -> foundTypes.add(it::class)
                is HomeRowConfig.TmdbWatchlist -> foundTypes.add(it::class)
                is HomeRowConfig.TmdbShelf -> foundTypes.add(it::class)
            }
        }
        Assert.assertEquals(HomeRowConfig::class.sealedSubclasses.size, foundTypes.size)
    }

    @Test
    fun `Print sample JSON`() {
        // This just prints out the JSON of the samples so developers can review
        val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            }
        val string = json.encodeToString(SAMPLES)
        println(string)
        json.decodeFromString<List<HomeRowConfig>>(string)
    }

    @Test
    fun `Parse list of rows with an unknown type`() {
        val service =
            HomeSettingsService(
                context = mockk(),
                api = mockk(),
                serverRepository = mockk(),
                userPreferencesService = mockk(),
                navDrawerService = mockk(),
                latestNextUpService = mockk(),
                imageUrlService = mockk(),
                suggestionService = mockk(),
                displayPreferencesService = mockk(),
                catalogRepository = mockk(relaxed = true),
                recommender = mockk(relaxed = true),
                tmdbAccount = mockk(relaxed = true),
            )

        val str = """{
            "type": "HomePageSettings",
            "version": 1,
            "rows": [
                {
                    "type": "RecentlyAdded",
                    "parentId": "1dd1c2fd-2e1b-48e4-ba94-17a2350fe9cf"
                },
                {
                    "type": "Does not exist",
                    "viewOptions": {}
                }
            ]
        }"""

        val jsonElement = service.jsonParser.parseToJsonElement(str)
        val settings = service.decode(jsonElement)
        Assert.assertEquals(1, settings.rows.size)
    }
}
