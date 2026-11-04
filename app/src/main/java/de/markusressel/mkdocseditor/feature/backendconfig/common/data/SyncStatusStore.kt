package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers when the app last got an answer from a backend, so that a stored copy can say how old it is after a restart
 */
@Singleton
internal class SyncStatusStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("sync_status", Context.MODE_PRIVATE)

    fun lastSync(backendId: Long): Date? =
        preferences.getLong(key(backendId), 0).takeIf { it > 0 }?.let { Date(it) }

    fun setLastSync(backendId: Long, time: Date) = preferences.edit {
        putLong(key(backendId), time.time)
    }

    private fun key(backendId: Long) = "last_sync_$backendId"
}
