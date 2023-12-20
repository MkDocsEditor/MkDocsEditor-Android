package de.markusressel.mkdocseditor.dagger

import android.content.Context
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
