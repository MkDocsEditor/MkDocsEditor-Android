package de.markusressel.mkdocseditor.application

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.HiltAndroidApp
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.application.log.FileTree
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.event.subscribe
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.MigrateBackendBasicAuthUseCase
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.network.domain.ScheduleOfflineCacheUpdateUseCase
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import de.markusressel.mkdocseditor.service.AccessRequestsWorker
import de.markusressel.mkdocseditor.service.UnsyncedChangesScheduler
import de.markusressel.mkdocseditor.service.WikiChangesSignInTrigger
import de.markusressel.mkdocseditor.service.WikiChangesWorker
import javax.inject.Inject
import javax.inject.Provider

/**
 * Created by Markus on 20.12.2017.
 */
@HiltAndroidApp
class App : Application(), Configuration.Provider, SingletonImageLoader.Factory {

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

    @Inject
    internal lateinit var unsyncedChangesScheduler: UnsyncedChangesScheduler

    @Inject
    internal lateinit var eventBusManager: EventBusManager

    @Inject
    internal lateinit var wikiChangesSignInTrigger: WikiChangesSignInTrigger

    @Inject
    internal lateinit var migrateBackendBasicAuthUseCase: MigrateBackendBasicAuthUseCase

    @Inject
    internal lateinit var imageLoader: Provider<ImageLoader>

    // AsyncImage uses this one, which knows how to load profile pictures
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

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

        migrateBackendBasicAuthUseCase()

        listenToEvents()

        scheduleOfflineCacheUpdate()

        unsyncedChangesScheduler.start(MainScope())

        AccessRequestsWorker.schedule(this)

        if (preferencesHolder.wikiChangeNotificationsPreference.persistedValue.value) WikiChangesWorker.schedule(this)
        else WikiChangesWorker.cancel(this)
        wikiChangesSignInTrigger.start(MainScope())
    }

    private fun listenToEvents() {
        eventBusManager.observe<BusEvent.SettingsEvent.ScheduleOfflineCacheUpdateRequestEvent>()
            .subscribe { event ->
                runCatching {
                    scheduleOfflineCacheUpdateUseCase()
                }.onFailure {
                    Timber.e(it) { "Failed to schedule offline cache update" }
                }
            }

        eventBusManager.observe<BusEvent.DebugEvent.LogNetworkRequestsChangedEvent>()
            .subscribe { event ->
            if (event.enabled) {
                mkDocsRestClient.enableLogging()
            } else {
                mkDocsRestClient.disableLogging()
            }
        }
        preferencesHolder.logNetworkRequests.persistedValue.let {
            eventBusManager.send(BusEvent.DebugEvent.LogNetworkRequestsChangedEvent(it.value))
        }
    }

    private fun setupErrorHandlers() {
    }

    private fun scheduleOfflineCacheUpdate() {
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

}
