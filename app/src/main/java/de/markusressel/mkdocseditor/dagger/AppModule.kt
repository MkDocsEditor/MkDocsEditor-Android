package de.markusressel.mkdocseditor.dagger

import android.app.Application
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import de.markusressel.kutepreferences.library.persistence.DefaultKutePreferenceDataProvider
import de.markusressel.kutepreferences.library.persistence.KutePreferenceDataProvider
import de.markusressel.mkdocseditor.application.App
import de.markusressel.mkdocseditor.data.persistence.entity.MyObjectBox
import de.markusressel.mkdocseditor.view.activity.EditorActivity
import de.markusressel.mkdocseditor.view.activity.MainActivity
import de.markusressel.mkdocseditor.view.activity.base.DaggerSupportActivityBase
import de.markusressel.mkdocseditor.view.fragment.DocumentsFragment
import de.markusressel.mkdocseditor.view.fragment.EditorFragment
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import de.markusressel.mkdocseditor.view.fragment.preferences.PreferencesFragment
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import io.objectbox.BoxStore
import javax.inject.Singleton

/**
 * Created by Markus on 20.12.2017.
 */
@Module
abstract class AppModule {

    @Binds
    internal abstract fun application(application: App): Application

    @ContributesAndroidInjector
    internal abstract fun DaggerSupportActivityBase(): DaggerSupportActivityBase

    @ContributesAndroidInjector
    internal abstract fun MainActivity(): MainActivity

    @ContributesAndroidInjector
    internal abstract fun DocumentsFragment(): DocumentsFragment

    @ContributesAndroidInjector
    internal abstract fun EditorActivity(): EditorActivity

    @ContributesAndroidInjector
    internal abstract fun EditorFragment(): EditorFragment

    @ContributesAndroidInjector
    internal abstract fun PreferencesFragment(): PreferencesFragment

    @Module
    companion object {

        @Provides
        @Singleton
        @JvmStatic
        internal fun provideContext(application: Application): Context {
            return application
        }

        @Provides
        @Singleton
        @JvmStatic
        internal fun provideKutePreferenceDataProvider(context: Context): KutePreferenceDataProvider {
            return DefaultKutePreferenceDataProvider(context)
        }

        @Provides
        @Singleton
        @JvmStatic
        internal fun provideMkDocsRestClient(preferenceHolder: KutePreferencesHolder): MkDocsRestClient {
            val restClient = MkDocsRestClient()

            restClient
                    .setHostname(preferenceHolder.connectionUriPreference.persistedValue)
            restClient
                    .setBasicAuthConfig(BasicAuthConfig(username = preferenceHolder.basicAuthUserPreference.persistedValue, password = preferenceHolder.basicAuthPasswordPreference.persistedValue))

            return restClient
        }

        @Provides
        @Singleton
        @JvmStatic
        internal fun provideBoxStore(context: Context): BoxStore {
            return MyObjectBox
                    .builder()
                    .androidContext(context)
                    .build()
        }

    }

}
