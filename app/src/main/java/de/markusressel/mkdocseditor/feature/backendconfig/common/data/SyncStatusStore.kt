package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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

    /** counts the writes, so a screen in the back stack sees a sync made elsewhere, e.g. in the editor */
    private val writes = MutableStateFlow(0)

    fun lastSync(backendId: Long): Date? =
        preferences.getLong(key(backendId), 0).takeIf { it > 0 }?.let { Date(it) }

    /** [lastSync] right away, and again whenever it changes */
    fun lastSyncFlow(backendId: Long): Flow<Date?> = writes.map { lastSync(backendId) }.distinctUntilChanged()

    fun setLastSync(backendId: Long, time: Date) {
        preferences.edit { putLong(key(backendId), time.time) }
        writes.update { it + 1 }
    }

    private fun key(backendId: Long) = "last_sync_$backendId"
}
