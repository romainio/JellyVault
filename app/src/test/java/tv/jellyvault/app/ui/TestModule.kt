package tv.jellyvault.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.JellyfinOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import tv.jellyvault.app.BuildConfig
import tv.jellyvault.app.R
import tv.jellyvault.app.data.AppDatabase
import tv.jellyvault.app.data.ItemPlaybackDao
import tv.jellyvault.app.data.JellyfinServerDao
import tv.jellyvault.app.data.LibraryDisplayInfoDao
import tv.jellyvault.app.data.Migrations
import tv.jellyvault.app.data.PlaybackEffectDao
import tv.jellyvault.app.data.PlaybackLanguageChoiceDao
import tv.jellyvault.app.data.RememberedTabDao
import tv.jellyvault.app.data.ServerPreferencesDao
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.preferences.AppPreferencesSerializer
import tv.jellyvault.app.services.hilt.AppModule
import tv.jellyvault.app.services.hilt.AuthOkHttpClient
import tv.jellyvault.app.services.hilt.DatabaseModule
import tv.jellyvault.app.services.hilt.DefaultCoroutineScope
import tv.jellyvault.app.services.hilt.DefaultDispatcher
import tv.jellyvault.app.services.hilt.DeviceModule
import tv.jellyvault.app.services.hilt.IoCoroutineScope
import tv.jellyvault.app.services.hilt.IoDispatcher
import tv.jellyvault.app.services.hilt.StandardOkHttpClient
import tv.jellyvault.app.util.CoroutineContextApiClientFactory
import tv.jellyvault.app.util.JvDispatchers
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DeviceModule::class, AppModule::class],
)
object TestModule {
    val testDispatcher = StandardTestDispatcher()

    @Provides
    @Singleton
    fun deviceInfo(
        @ApplicationContext context: Context,
    ): DeviceInfo = DeviceInfo("test_device_id", "test_device")

    @Provides
    @Singleton
    fun clientInfo(
        @ApplicationContext context: Context,
    ): ClientInfo =
        ClientInfo(
            name = context.getString(R.string.app_name),
            version = BuildConfig.VERSION_NAME,
        )

    @StandardOkHttpClient
    @Provides
    @Singleton
    fun okHttpClient() =
        OkHttpClient
            .Builder()
            .apply {
                // TODO user agent, timeouts, logging, etc
            }.build()

    @AuthOkHttpClient
    @Provides
    @Singleton
    fun authOkHttpClient(
        serverRepository: ServerRepository,
        @StandardOkHttpClient okHttpClient: OkHttpClient,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
    ) = okHttpClient
        .newBuilder()
        .addInterceptor {
            val request = it.request()
            val newRequest =
                serverRepository.currentUser?.accessToken?.let { token ->
                    request
                        .newBuilder()
                        .addHeader(
                            "Authorization",
                            AuthorizationHeaderBuilder.buildHeader(
                                clientName = clientInfo.name,
                                clientVersion = clientInfo.version,
                                deviceId = deviceInfo.id,
                                deviceName = deviceInfo.name,
                                accessToken = token,
                            ),
                        ).build()
                }
            it.proceed(newRequest ?: request)
        }.build()

    @Provides
    @Singleton
    fun okHttpFactory(
        @StandardOkHttpClient okHttpClient: OkHttpClient,
    ) = CoroutineContextApiClientFactory(OkHttpFactory(okHttpClient))

    @Provides
    @Singleton
    fun jellyfin(
        okHttpFactory: CoroutineContextApiClientFactory,
        @ApplicationContext context: Context,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
    ): Jellyfin {
        val jellyfin: Jellyfin = mockk()
        every { jellyfin.clientInfo } returns clientInfo
        every { jellyfin.deviceInfo } returns deviceInfo
        every { jellyfin.options } returns
            JellyfinOptions(
                context,
                clientInfo,
                deviceInfo,
                okHttpFactory,
                okHttpFactory,
                Jellyfin.minimumVersion,
            )
        every { jellyfin.createApi(any()) } returns apiClient(jellyfin)
        every { jellyfin.createApi(any(), any(), any(), any(), any()) } returns apiClient(jellyfin)
        return jellyfin
    }

    @Provides
    @Singleton
    fun apiClient(jellyfin: Jellyfin): ApiClient {
        val api: ApiClient = mockk()
        every { api.clientInfo } returns jellyfin.clientInfo!!
        every { api.deviceInfo } returns jellyfin.deviceInfo!!
        every { api.update(any(), any(), any(), any()) } returns Unit
        return api
    }

    @Provides
    @Singleton
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = testDispatcher

    @Provides
    @Singleton
    @IoCoroutineScope
    fun ioCoroutineScope(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = testDispatcher

    @Provides
    @Singleton
    @DefaultCoroutineScope
    fun defaultCoroutineScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun workManager(
        @ApplicationContext context: Context,
    ): WorkManager = mockk()
}

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class],
)
object TestDatabaseModule {
    @Module
    @InstallIn(SingletonComponent::class)
    object DatabaseModule {
        @Provides
        @Singleton
        fun database(
            @ApplicationContext context: Context,
        ): AppDatabase =
            Room
                .inMemoryDatabaseBuilder(
                    context,
                    AppDatabase::class.java,
                ).addMigrations(Migrations.Migrate2to3)
                .allowMainThreadQueries()
                .setQueryCallback({ sqlQuery, args ->
                    Timber.v("sqlQuery=$sqlQuery, args=$args")
                }, JvDispatchers.IO.asExecutor())
                .build()

        @Provides
        @Singleton
        fun serverDao(db: AppDatabase): JellyfinServerDao = db.serverDao()

        @Provides
        @Singleton
        fun itemPlaybackDao(db: AppDatabase): ItemPlaybackDao = db.itemPlaybackDao()

        @Provides
        @Singleton
        fun serverPreferencesDao(db: AppDatabase): ServerPreferencesDao = db.serverPreferencesDao()

        @Provides
        @Singleton
        fun libraryDisplayInfoDao(db: AppDatabase): LibraryDisplayInfoDao = db.libraryDisplayInfoDao()

        @Provides
        @Singleton
        fun playbackLanguageChoiceDao(db: AppDatabase): PlaybackLanguageChoiceDao = db.playbackLanguageChoiceDao()

        @Provides
        @Singleton
        fun playbackEffectDao(db: AppDatabase): PlaybackEffectDao = db.playbackEffectDao()

        @Provides
        @Singleton
        fun rememberedTabDao(db: AppDatabase): RememberedTabDao = db.rememberedTabDao()

        @Provides
        @Singleton
        fun userPreferencesDataStore(
            @ApplicationContext context: Context,
            userPreferencesSerializer: AppPreferencesSerializer,
        ): DataStore<AppPreferences> =
            DataStoreFactory.create(
                serializer = userPreferencesSerializer,
                produceFile = { context.dataStoreFile("app_preferences.pb") },
                corruptionHandler =
                    ReplaceFileCorruptionHandler(
                        produceNewData = { AppPreferences.getDefaultInstance() },
                    ),
            )

        @Provides
        @Singleton
        fun keyValueDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("key_value") },
            )
    }
}
