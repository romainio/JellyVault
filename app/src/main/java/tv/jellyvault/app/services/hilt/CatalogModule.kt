package tv.jellyvault.app.services.hilt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import tv.jellyvault.app.BuildConfig
import tv.jellyvault.app.catalog.SeenTitles
import tv.jellyvault.app.catalog.account.TmdbAccountClient
import tv.jellyvault.app.catalog.account.TmdbAccountRepository
import tv.jellyvault.app.catalog.arr.ArrSettings
import tv.jellyvault.app.catalog.arr.ArrSettingsSerializer
import tv.jellyvault.app.catalog.key
import tv.jellyvault.app.catalog.library.JellyfinLibraryItemSource
import tv.jellyvault.app.catalog.library.LibraryItemSource
import tv.jellyvault.app.catalog.shelves.LocalShelfSource
import tv.jellyvault.app.catalog.shelves.ShelfSource
import tv.jellyvault.app.catalog.taste.JellyfinTasteSource
import tv.jellyvault.app.catalog.taste.TasteAffinityStore
import tv.jellyvault.app.catalog.taste.TasteSource
import tv.jellyvault.app.catalog.tmdb.TmdbCredential
import tv.jellyvault.app.catalog.tmdb.TmdbKeyStore
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.ui.detail.tmdb.JellyfinOwnedPlaybackSource
import tv.jellyvault.app.ui.detail.tmdb.OwnedPlaybackSource
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    @Singleton
    fun tmdbKeyStore(
        @ApplicationContext context: Context,
    ): TmdbKeyStore = TmdbKeyStore(File(context.noBackupFilesDir, TMDB_KEY_FILE), BuildConfig.TMDB_READ_TOKEN)

    @Provides
    fun tmdbCredential(store: TmdbKeyStore): TmdbCredential = store

    @Provides
    @Singleton
    fun arrSettingsDataStore(
        @ApplicationContext context: Context,
        serializer: ArrSettingsSerializer,
    ): DataStore<ArrSettings> =
        DataStoreFactory.create(
            serializer = serializer,
            produceFile = { context.dataStoreFile(ARR_SETTINGS_FILE) },
            corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { ArrSettings.getDefaultInstance() }),
        )

    @Provides
    @Singleton
    fun tasteAffinityStore(
        @ApplicationContext context: Context,
    ): TasteAffinityStore = TasteAffinityStore(File(context.filesDir, TASTE_DIR))

    @Provides
    @Singleton
    fun shelfSource(
        @ApplicationContext context: Context,
        affinityStore: TasteAffinityStore,
        serverRepository: ServerRepository,
    ): ShelfSource = LocalShelfSource(File(context.filesDir, TASTE_DIR), affinityStore) { serverRepository.currentUser?.id }

    @Provides
    @Singleton
    fun tmdbAccountRepository(
        @ApplicationContext context: Context,
        api: TmdbAccountClient,
        @IoCoroutineScope scope: CoroutineScope,
        serverRepository: ServerRepository,
    ): TmdbAccountRepository =
        TmdbAccountRepository(api, File(context.noBackupFilesDir, TMDB_ACCOUNT_DIR), scope) { serverRepository.currentUser?.id }

    @Provides
    fun seenTitles(account: TmdbAccountRepository): SeenTitles =
        SeenTitles {
            account
                .snapshot()
                .ratings
                .map { it.item.key }
                .toSet()
        }

    const val ARR_SETTINGS_FILE = "arr_settings.pb"
    const val TASTE_DIR = "taste"
    const val TMDB_KEY_FILE = "tmdb_key"

    /** In no-backup storage, like the Radarr/Sonarr keys: the session never leaves the device */
    const val TMDB_ACCOUNT_DIR = "tmdb_account"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogBindingsModule {
    @Binds
    abstract fun libraryItemSource(impl: JellyfinLibraryItemSource): LibraryItemSource

    @Binds
    abstract fun ownedPlaybackSource(impl: JellyfinOwnedPlaybackSource): OwnedPlaybackSource

    @Binds
    abstract fun tasteSource(impl: JellyfinTasteSource): TasteSource
}
