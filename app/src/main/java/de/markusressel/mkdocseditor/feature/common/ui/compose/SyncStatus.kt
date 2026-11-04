package de.markusressel.mkdocseditor.feature.common.ui.compose

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.markusressel.mkdocseditor.R
import kotlinx.coroutines.delay
import java.util.Date
import java.util.concurrent.TimeUnit

internal data class SyncAge(@param:PluralsRes val text: Int, val count: Int)

/** @return how long ago [lastSync] was, null for less than a minute */
internal fun syncAge(lastSync: Date, now: Date): SyncAge? {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(now.time - lastSync.time)
    return when {
        minutes < 1 -> null
        minutes < 60 -> SyncAge(R.plurals.synced_minutes, minutes.toInt())
        minutes < 24 * 60 -> SyncAge(R.plurals.synced_hours, (minutes / 60).toInt())
        else -> SyncAge(R.plurals.synced_days, (minutes / (24 * 60)).toInt())
    }
}

/**
 * One short line about how current the shown data is
 *
 * @param lastSync when the backend last answered, null if it never did
 * @param lastAttemptFailed the backend did not answer the last time, so what shows may be out of date
 * @return the text, null if there is nothing to say yet
 */
@Composable
internal fun syncStatusText(lastSync: Date?, lastAttemptFailed: Boolean): String? {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(lastSync) {
        if (lastSync == null) return@LaunchedEffect
        while (true) {
            now = Date()
            delay(TimeUnit.MINUTES.toMillis(1))
        }
    }

    val age = lastSync?.let { syncAge(it, now) }
    val synced = when {
        lastSync == null -> null
        age == null -> stringResource(R.string.synced_just_now)
        else -> pluralStringResource(age.text, age.count, age.count)
    }
    return when {
        !lastAttemptFailed -> synced
        synced == null -> stringResource(R.string.sync_not_connected)
        else -> stringResource(R.string.sync_not_connected_since, synced)
    }
}
