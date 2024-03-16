package de.markusressel.mkdocseditor.dagger

import android.accounts.AccountManager
import android.app.DownloadManager
import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.markusressel.kutepreferences.core.DefaultKuteNavigator
import de.markusressel.kutepreferences.core.KuteNavigator
import de.markusressel.kutepreferences.core.persistence.DefaultKutePreferenceDataProvider
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.mkdocseditor.data.persistence.entity.MyObjectBox
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocsrestclient.DummyMkDocsRestClient
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import io.objectbox.BoxStore
import javax.inject.Singleton

/**
 * Created by Markus on 20.12.2017.
 */
@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideAccountManager(context: Context): AccountManager {
        return AccountManager.get(context)
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(context: Context): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    @Singleton
    fun provideDownloadManager(context: Context): DownloadManager {
        return context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    @Provides
    @Singleton
    fun provideUiModeManager(context: Context): UiModeManager =
        (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)

    @Provides
    @Singleton
    fun provideWifiManager(context: Context): WifiManager {
        return context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    @Provides
    @Singleton
    fun provideContentResolver(context: Context): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Singleton
    fun provideKuteNavigator(): KuteNavigator {
        return DefaultKuteNavigator()
    }

    @Provides
    @Singleton
    fun provideKutePreferenceDataProvider(@ApplicationContext context: Context): KutePreferenceDataProvider {
        return DefaultKutePreferenceDataProvider(context)
    }

    @Provides
    @Singleton
    fun provideDiffMatchPatch(): diff_match_patch {
        return diff_match_patch()
    }

    @Provides
    @Singleton
    fun provideMkDocsRestClient(kutePreferencesHolder: KutePreferencesHolder): IMkDocsRestClient {
        return when (kutePreferencesHolder.demoMode.persistedValue.value) {
            true -> DummyMkDocsRestClient()
            else -> MkDocsRestClient()
        }
    }

    @Provides
    @Singleton
    fun provideBoxStore(@ApplicationContext context: Context): BoxStore {
        val store = MyObjectBox
            .builder()
            .androidContext(context)
            .build()

        // Clear DB entirely
//        store.close()
//        store.deleteAllFiles()

        return store
    }

}
