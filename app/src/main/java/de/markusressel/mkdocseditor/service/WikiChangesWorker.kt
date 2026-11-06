package de.markusressel.mkdocseditor.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.markusressel.mkdocseditor.auth.token.SharedPreferencesKeyValueStore
import de.markusressel.mkdocseditor.feature.notifications.data.RestWikiChangesServer
import de.markusressel.mkdocseditor.feature.notifications.data.WikiChangesMarks
import de.markusressel.mkdocseditor.feature.notifications.domain.CheckWikiChangesUseCase
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesNotifier
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesServer
import de.markusressel.mkdocseditor.feature.notifications.ui.AndroidWikiChangesNotifier
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import java.util.concurrent.TimeUnit

/**
 * See [CheckWikiChangesUseCase]. Most of the time nothing happens in a home wiki, so once an hour is enough, until
 * the server can push.
 */
@HiltWorker
internal class WikiChangesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val checkWikiChanges: CheckWikiChangesUseCase,
    private val preferencesHolder: KutePreferencesHolder,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!preferencesHolder.wikiChangeNotificationsPreference.persistedValue.value) return Result.success()
        return if (checkWikiChanges()) Result.success() else Result.retry()
    }

    companion object {
        internal const val WORK_NAME = "check-wiki-changes"
        internal const val NOW_WORK_NAME = "check-wiki-changes-now"
        private const val INTERVAL_MINUTES = 60L
        private const val FLEX_MINUTES = 20L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WikiChangesWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES, FLEX_MINUTES, TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(NOW_WORK_NAME)
        }

        /**
         * Checks once, as soon as there is a network. After signing in this sets the seen mark at once, instead of
         * an hour later, so changes from then on notify.
         */
        fun checkNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WikiChangesWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WikiChangesModule {
    @Binds
    abstract fun bindServer(impl: RestWikiChangesServer): WikiChangesServer

    @Binds
    abstract fun bindNotifier(impl: AndroidWikiChangesNotifier): WikiChangesNotifier

    companion object {
        @Provides
        fun provideMarks(@ApplicationContext context: Context) = WikiChangesMarks(
            SharedPreferencesKeyValueStore(context.getSharedPreferences("wiki_changes_seen", Context.MODE_PRIVATE))
        )
    }
}
