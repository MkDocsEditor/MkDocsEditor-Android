package de.markusressel.mkdocseditor.event

sealed interface BusEvent {
    data class LogNetworkRequestsChangedEvent(val enabled: Boolean)

    /**
     * Event that indicates a state change of the offline mode
     */
    data class OfflineModeChangedEvent(val enabled: Boolean)

    data object ScheduleOfflineCacheUpdateRequestEvent

    data class ThemeChangedEvent(val theme: String)
}
