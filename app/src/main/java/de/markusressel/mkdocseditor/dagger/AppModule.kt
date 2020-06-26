package de.markusressel.mkdocseditor.dagger

import android.app.Application
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.FragmentComponent
import de.markusressel.kutepreferences.core.persistence.DefaultKutePreferenceDataProvider
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.mkdocseditor.application.App
import de.markusressel.mkdocseditor.data.persistence.entity.MyObjectBox
import de.markusressel.mkdocseditor.service.OfflineCacheSyncService
import de.markusressel.mkdocseditor.view.activity.MainActivity
import de.markusressel.mkdocseditor.view.activity.base.DaggerSupportActivityBase
import de.markusressel.mkdocseditor.view.fragment.AboutPage
import de.markusressel.mkdocseditor.view.fragment.CodeEditorFragment
import de.markusressel.mkdocseditor.view.fragment.FileBrowserFragment
import de.markusressel.mkdocseditor.view.fragment.preferences.PreferencesFragment
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import io.objectbox.BoxStore
import javax.inject.Singleton

/**
 * Created by Markus on 20.12.2017.
 */
@Module
@InstallIn(ActivityComponent::class, FragmentComponent::class)
abstract class AppModule {

    @Binds
    internal abstract fun application(application: App): Application

    @ContributesAndroidInjector
    internal abstract fun DaggerJobService(): DaggerJobService

    @ContributesAndroidInjector
    internal abstract fun OfflineCacheSyncService(): OfflineCacheSyncService

    @ContributesAndroidInjector
    internal abstract fun DaggerSupportActivityBase(): DaggerSupportActivityBase

    @ContributesAndroidInjector
    internal abstract fun MainActivity(): MainActivity

    @ContributesAndroidInjector
    internal abstract fun DocumentsFragment(): FileBrowserFragment

    @ContributesAndroidInjector
    internal abstract fun EditorFragment(): CodeEditorFragment

    @ContributesAndroidInjector
    internal abstract fun AboutPage(): AboutPage

    @ContributesAndroidInjector
    internal abstract fun PreferencesFragment(): PreferencesFragment

    @Module
    @InstallIn(ActivityComponent::class, FragmentComponent::class)
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
        internal fun provideDiffMatchPatch(): diff_match_patch {
            return diff_match_patch()
        }

        @Provides
        @Singleton
        @JvmStatic
        internal fun provideMkDocsRestClient(): MkDocsRestClient {
            return MkDocsRestClient()
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
