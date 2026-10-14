package tv.jellyvault.app.services

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jellyvault.app.LegacyNames
import tv.jellyvault.app.preferences.AppPreference
import tv.jellyvault.app.preferences.AppPreferences
import tv.jellyvault.app.services.tvprovider.TvProviderWorker
import tv.jellyvault.app.util.Version

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PackageRenameMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Test
    fun `cancels the work of the old worker classes and prunes it`() {
        PackageRenameMigration(context, workManager).run()

        LegacyNames.WORK_NAMES.forEach { verify { workManager.cancelUniqueWork(it) } }
        verify { workManager.pruneWork() }
        verify(exactly = 0) { workManager.cancelAllWork() }
    }

    @Test
    fun `the work names of the current workers are not cancelled as legacy`() {
        val current =
            listOf(SuggestionsWorker.WORK_NAME, LatestNextUpWorker.WORK_NAME, TvProviderWorker.WORK_NAME)

        current.forEach { assertFalse("$it must not be a legacy name", it in LegacyNames.WORK_NAMES) }
    }

    @Test
    fun `forgets the TV channel so the worker recreates it with the new activity`() {
        val channels = context.getSharedPreferences("channels", Context.MODE_PRIVATE)
        channels.edit().putString("latest", "content://android.media.tv/channel/7").commit()

        PackageRenameMigration(context, workManager).run()

        assertNull(channels.getString("latest", null))
    }

    @Test
    fun `survives a device without a TV provider`() {
        // Robolectric has no android.media.tv provider: deleting rows must not throw
        PackageRenameMigration(context, workManager).run()
    }

    @Test
    fun `the migration runs once when upgrading from a build before 1_1_0`() {
        val migration = mockk<PackageRenameMigration>(relaxed = true)

        runBlocking {
            handler(migration).upgradeApp(
                Version.fromString("1.0.8-135-g0"),
                Version.fromString("1.1.0"),
                FakeStore(),
            )
        }

        verify(exactly = 1) { migration.run() }
    }

    @Test
    fun `the migration is skipped when already on 1_1_0 or later`() {
        val migration = mockk<PackageRenameMigration>(relaxed = true)

        runBlocking {
            handler(migration).upgradeApp(
                Version.fromString("1.1.0"),
                Version.fromString("1.1.1"),
                FakeStore(),
            )
        }

        verify(exactly = 0) { migration.run() }
    }

    @Test
    fun `the update URL of the old project is replaced by the default one`() {
        val store = FakeStore(AppPreferences.newBuilder().setUpdateUrl(LegacyNames.UPDATE_URL).build())

        runBlocking {
            handler(mockk(relaxed = true)).upgradeApp(
                Version.fromString("1.0.8-135-g0"),
                Version.fromString("1.1.0"),
                store,
            )
        }

        assertEquals(AppPreference.UpdateUrl.defaultValue, store.state.value.updateUrl)
    }

    @Test
    fun `the update URL of a former repository owner name is replaced at any upgrade`() {
        val store =
            FakeStore(AppPreferences.newBuilder().setUpdateUrl("https://api.github.com/repos/old-name/JellyVault/releases/latest").build())

        runBlocking {
            handler(mockk(relaxed = true)).upgradeApp(
                Version.fromString("1.1.0"),
                Version.fromString("1.1.1"),
                store,
            )
        }

        assertEquals(AppPreference.UpdateUrl.defaultValue, store.state.value.updateUrl)
    }

    @Test
    fun `a custom update URL is kept`() {
        val custom = "https://example.org/releases/latest"
        val store = FakeStore(AppPreferences.newBuilder().setUpdateUrl(custom).build())

        runBlocking {
            handler(mockk(relaxed = true)).upgradeApp(
                Version.fromString("1.0.8-135-g0"),
                Version.fromString("1.1.0"),
                store,
            )
        }

        assertEquals(custom, store.state.value.updateUrl)
    }

    @Test
    fun `an unreadable stored version still runs the upgrades`() {
        val migration = mockk<PackageRenameMigration>(relaxed = true)
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .putString(AppUpgradeHandler.VERSION_NAME_CURRENT_KEY, "not a version")
            .commit()

        runBlocking { handler(migration).run() }

        verify(exactly = 1) { migration.run() }
    }

    private fun handler(migration: PackageRenameMigration) =
        AppUpgradeHandler(context, FakeStore(), mockk(relaxed = true), mockk(relaxed = true), migration)

    private class FakeStore(
        initial: AppPreferences = AppPreferences.getDefaultInstance(),
    ) : DataStore<AppPreferences> {
        val state = MutableStateFlow(initial)
        override val data: Flow<AppPreferences> get() = state

        override suspend fun updateData(transform: suspend (t: AppPreferences) -> AppPreferences): AppPreferences {
            state.value = transform(state.value)
            return state.value
        }
    }
}
