package tv.jellyvault.app.services.hilt

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import tv.jellyvault.app.BuildConfig
import tv.jellyvault.app.R
import tv.jellyvault.app.data.ServerRepository
import tv.jellyvault.app.util.CoroutineContextApiClientFactory
import tv.jellyvault.app.util.JvDispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * An [OkHttpClient] that includes the user's access token when making requests
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthOkHttpClient

/**
 * A basic [OkHttpClient] that does not include auth
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StandardOkHttpClient

/**
 * A [CoroutineScope] with [JvDispatchers.IO]
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoCoroutineScope

/**
 * A [CoroutineScope] with [JvDispatchers.Default]
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultCoroutineScope

/**
 * [JvDispatchers.IO]
 *
 * @see IoCoroutineScope
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * [JvDispatchers.Default]
 *
 * @see DefaultCoroutineScope
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
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
                serverRepository.current.value?.user?.accessToken?.let { token ->
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
    ): Jellyfin =
        createJellyfin {
            this.context = context
            this.clientInfo = clientInfo
            this.deviceInfo = deviceInfo
            apiClientFactory = okHttpFactory
            socketConnectionFactory = okHttpFactory
            minimumServerVersion = Jellyfin.minimumVersion
        }

    @Provides
    @Singleton
    fun apiClient(jellyfin: Jellyfin) = jellyfin.createApi()

    @Provides
    @Singleton
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = JvDispatchers.IO

    @Provides
    @Singleton
    @IoCoroutineScope
    fun ioCoroutineScope(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = JvDispatchers.Default

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
    ): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
object DeviceModule {
    @Provides
    @Singleton
    fun deviceInfo(
        @ApplicationContext context: Context,
    ): DeviceInfo = androidDevice(context)
}
