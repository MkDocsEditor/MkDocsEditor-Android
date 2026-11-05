package de.markusressel.mkdocseditor.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.token.PendingAccessRequest
import de.markusressel.mkdocseditor.auth.token.SharedPreferencesKeyValueStore
import de.markusressel.mkdocseditor.ui.activity.MainActivityCompose
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** See [AccessRequestChecker]. Every 15 minutes is the most Android allows for background work. */
@HiltWorker
internal class AccessRequestsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val checker: AccessRequestChecker,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = if (checker()) Result.success() else Result.retry()

    companion object {
        private const val WORK_NAME = "check-access-requests"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AccessRequestsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

internal class AndroidAccessRequestNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : AccessRequestNotifier {

    override fun onRequestAnswered(request: PendingAccessRequest, approved: Boolean) {
        // opens the link again, whose screen finds the stored request and goes on with the invite
        val intent = Intent(Intent.ACTION_VIEW, request.joinLink.toUri(), context, MainActivityCompose::class.java)
        val text = if (approved) R.string.access_request_notification_approved else R.string.access_request_notification_rejected
        post(NOTIFICATION_ANSWER, context.getString(R.string.access_request_notification_title, request.serverName), context.getString(text), intent)
    }

    override fun onNewRequests(backendId: Long, backendName: String, count: Int) {
        val intent = Intent(context, MainActivityCompose::class.java).putExtra(MainActivityCompose.EXTRA_ACCESS_REQUESTS_OF_BACKEND, backendId)
        val text = context.resources.getQuantityString(R.plurals.access_request_notification_new, count, count)
        post(NOTIFICATION_NEW + backendId.toInt(), backendName, text, intent)
    }

    private fun post(id: Int, title: String, text: String, intent: Intent) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.access_request_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }

    private companion object {
        const val CHANNEL = "access_requests"
        const val NOTIFICATION_ANSWER = 7300
        const val NOTIFICATION_NEW = 7301
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccessRequestModule {
    @Binds
    abstract fun bindNotifier(impl: AndroidAccessRequestNotifier): AccessRequestNotifier

    companion object {
        @Provides
        fun provideSeenAccessRequests(@ApplicationContext context: Context) = SeenAccessRequests(
            SharedPreferencesKeyValueStore(context.getSharedPreferences("seen_access_requests", Context.MODE_PRIVATE))
        )
    }
}
