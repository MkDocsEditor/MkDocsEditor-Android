package de.markusressel.mkdocseditor.event

import android.net.Uri

sealed interface BusEvent {
    data class LogNetworkRequestsChangedEvent(val enabled: Boolean) : BusEvent

    /**
     * Event that indicates a state change of the offline mode
     */
    data class OfflineModeChangedEvent(val enabled: Boolean) : BusEvent

    data object ScheduleOfflineCacheUpdateRequestEvent : BusEvent

    data class ThemeChangedEvent(val theme: String) : BusEvent

    data class FilePickerResult(val uri: Uri?) : BusEvent
}
