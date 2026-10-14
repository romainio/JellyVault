package tv.jellyvault.app.services.hilt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tv.jellyvault.app.LegacyNames
import tv.jellyvault.app.data.AppDatabase
import tv.jellyvault.app.data.ItemPlaybackDao
import tv.jellyvault.app.data.JellyfinServerDao
import tv.jellyvault.app.data.LibraryDisplayInfoDao
import tv.jellyvault.app.data.Migrations
import tv.jellyvault.app.data.PlaybackEffectDao
import tv.jellyvault.app.data.PlaybackLanguageChoiceDao
import tv.jellyvault.app.data.RememberedTabDao
import tv.jellyvault.app.data.ServerPreferencesDao
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.preferences.AppPreferencesSerializer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                LegacyNames.DATABASE,
            ).addMigrations(Migrations.Migrate2to3)
//            .setQueryCallback({ sqlQuery, args ->
//                Timber.v("sqlQuery=$sqlQuery, args=$args")
//            }, JvDispatchers.IO.asExecutor())
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
