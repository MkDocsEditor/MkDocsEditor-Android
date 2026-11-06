package de.markusressel.mkdocseditor.di

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
import de.markusressel.mkdocseditor.data.persistence.base.WikiDatabase
import de.markusressel.mkdocseditor.data.persistence.entity.MyObjectBox
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.WikiScope
import de.markusressel.mkdocseditor.feature.auth.common.data.AndroidCredentialClient
import de.markusressel.mkdocseditor.feature.auth.common.data.CredentialClient
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.feature.theme.domain.ApplicationNightMode
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.BackendManager
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.createSelectedBackendRestClient
import de.markusressel.mkdocsrestclient.DummyMkDocsRestClient
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.reference.ItemType
import de.markusressel.mkdocsrestclient.sync.automerge.LocalCopyRedirects
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
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
    internal fun provideApplicationNightMode(uiModeManager: UiModeManager): ApplicationNightMode =
        ApplicationNightMode(uiModeManager::setApplicationNightMode)

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

    /**
     * Local Automerge copies of documents, so edits survive offline periods and app restarts
     */
    @Provides
    @Singleton
    fun provideLocalDocumentStore(wikiScope: WikiScope): LocalDocumentStore = wikiScope.documents

    @Provides
    @Singleton
    fun provideWikiDatabase(wikiScope: WikiScope): WikiDatabase = wikiScope.database

    /**
     * Re-keys local copies of documents that moved on the server
     */
    @Provides
    @Singleton
    fun provideLocalCopyRedirects(store: LocalDocumentStore, restClient: IMkDocsRestClient): LocalCopyRedirects {
        return LocalCopyRedirects(store) { oldIds ->
            restClient.getMoved(oldIds).fold(
                success = { answer ->
                    answer.moved.filter { it.item.type == ItemType.DOCUMENT }.associate { it.oldId to it.item.id }
                },
                failure = { throw it },
            )
        }
    }

    @Provides
    @Singleton
    fun provideKutePreferenceDataProvider(@ApplicationContext context: Context): KutePreferenceDataProvider {
        return DefaultKutePreferenceDataProvider(context)
    }

    @Provides
    @Singleton
    internal fun provideMkDocsRestClient(
        kutePreferencesHolder: KutePreferencesHolder,
        backendManager: BackendManager,
        authRepository: AuthRepository,
    ): IMkDocsRestClient {
        return when (kutePreferencesHolder.demoMode.persistedValue.value) {
            true -> DummyMkDocsRestClient()
            else -> createSelectedBackendRestClient(backendManager::getSelectedBackend, authRepository)
        }
    }

    @Provides
    @Singleton
    fun provideCredentialClient(): CredentialClient = AndroidCredentialClient()

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
