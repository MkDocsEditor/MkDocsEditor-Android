package de.markusressel.mkdocseditor.application

import android.app.Application
import androidx.emoji.bundled.BundledEmojiCompatConfig
import androidx.emoji.text.EmojiCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.eightbitlab.rxbus.Bus
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.HiltAndroidApp
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.application.log.FileTree
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.network.domain.ScheduleOfflineCacheUpdateUseCase
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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
    internal lateinit var documentPersistenceManager: DocumentPersistenceManager

    @Inject
    internal lateinit var getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase

    @Inject
    internal lateinit var mkDocsRestClient: IMkDocsRestClient

    @Inject
    internal lateinit var scheduleOfflineCacheUpdateUseCase: ScheduleOfflineCacheUpdateUseCase

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

        listenToEvents()

        initOfflineMode()
    }

    private fun listenToEvents() {
        Bus.observe<BusEvent.ScheduleOfflineCacheUpdateRequestEvent>().subscribe { event ->
            MainScope().launch {
                runCatching {
                    scheduleOfflineCacheUpdateUseCase(evenInOfflineMode = true)
                }.onFailure {
                    Timber.e(it) { "Failed to schedule offline cache update" }
                }
            }
        }

        Bus.observe<BusEvent.LogNetworkRequestsChangedEvent>().subscribe { event ->
            if (event.enabled) {
                mkDocsRestClient.enableLogging()
            } else {
                mkDocsRestClient.disableLogging()
            }
        }
        preferencesHolder.logNetworkRequests.persistedValue.let {
            Bus.send(BusEvent.LogNetworkRequestsChangedEvent(it.value))
        }
    }

    private fun setupErrorHandlers() {
    }

    private fun initOfflineMode() {
        MainScope().launch {
            runCatching {
                scheduleOfflineCacheUpdateUseCase()
            }.onFailure {
                Timber.e(it) { "Failed to schedule offline cache update" }
            }
        }
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
