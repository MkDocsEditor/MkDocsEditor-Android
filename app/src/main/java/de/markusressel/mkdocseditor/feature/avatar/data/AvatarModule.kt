package de.markusressel.mkdocseditor.feature.avatar.data

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AvatarModule {

    private const val DISK_CACHE_BYTES = 20L * 1024 * 1024

    /** The image loader of the whole app, see [de.markusressel.mkdocseditor.application.App] */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        backends: AuthBackends,
        authRepository: AuthRepository,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() }))
            add(AvatarKeyer)
            add(AvatarFetcher.Factory(backends, authRepository))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("avatars").toOkioPath())
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .build()
}
