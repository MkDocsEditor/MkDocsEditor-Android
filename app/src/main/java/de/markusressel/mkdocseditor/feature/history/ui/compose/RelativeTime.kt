package de.markusressel.mkdocseditor.feature.history.ui.compose

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.markusressel.mkdocseditor.R
import java.util.Date

/** within the last minute, or from a server whose clock is a little ahead, it is "just now", not "in 0 minutes" */
@Composable
internal fun relativeTime(time: Date): String {
    val now = System.currentTimeMillis()
    if (now - time.time < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.history_just_now)
    return DateUtils.getRelativeTimeSpanString(time.time, now, DateUtils.MINUTE_IN_MILLIS).toString()
}
