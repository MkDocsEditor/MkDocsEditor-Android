package de.markusressel.mkdocseditor.feature.notifications.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.R
import javax.inject.Inject
import javax.inject.Singleton

/** The channel of the notifications about changes in the wiki */
internal object WikiChangesChannel {
    const val ID = "wiki_changes"

    fun create(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ID, context.getString(R.string.wiki_changes_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

/**
 * Whether notifications about changes can reach the user, and whether the app has asked for them already
 */
@Singleton
internal class NotificationPermission @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences by lazy { context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** false if the permission is missing, or the user turned off the notifications of the app or of the channel */
    fun isGranted(): Boolean {
        if (!hasPermission()) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        return manager.getNotificationChannel(WikiChangesChannel.ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * The dashboard asks once, the first time it shows. Asking more often would only annoy: the setting asks again
     * when it is turned on.
     *
     * @return true if the dashboard should ask now
     */
    fun takeDashboardRequest(notificationsOn: Boolean): Boolean {
        if (!notificationsOn || hasPermission() || preferences.getBoolean(KEY_ASKED, false)) return false
        preferences.edit { putBoolean(KEY_ASKED, true) }
        return true
    }

    private companion object {
        const val PREFERENCES_NAME = "notification_permission"
        const val KEY_ASKED = "asked_on_dashboard"
    }
}
