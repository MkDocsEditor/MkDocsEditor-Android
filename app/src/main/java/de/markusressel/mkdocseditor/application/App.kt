package de.markusressel.mkdocseditor.application

import android.app.Application
import androidx.emoji.bundled.BundledEmojiCompatConfig
import androidx.emoji.text.EmojiCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.application.log.FileTree
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.network.OfflineModeManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Created by Markus on 20.12.2017.
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider {

    // used for WorkManager
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    internal lateinit var preferencesHolder: KutePreferencesHolder

    @Inject
    internal lateinit var offlineModeManager: OfflineModeManager

    @Inject
    internal lateinit var documentPersistenceManager: DocumentPersistenceManager

    @Inject
    internal lateinit var getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase


    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // register app lifecycle
        registerActivityLifecycleCallbacks(AppLifecycleTracker())

        plantTimberTrees()

        setupErrorHandlers()

        initializeEmojiCompat()

        initOfflineMode()
    }

    private fun setupErrorHandlers() {
    }

    private fun initOfflineMode() {
        offlineModeManager.scheduleOfflineCacheUpdate()
    }

    private fun plantTimberTrees() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(FileTree(this))
        }
    }

    private fun initializeEmojiCompat() {
        val config = BundledEmojiCompatConfig(this)
        EmojiCompat.init(config)
    }

}
