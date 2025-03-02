package de.markusressel.mkdocseditor.event

import android.net.Uri

sealed interface BusEvent {

    sealed interface FeatureEvent : BusEvent {
        sealed interface FilePickerEvent : FeatureEvent {
            /**
             * Event that indicates the result of a system-side file-picker dialog
             */
            data class FilePickerResult(val uri: Uri?) : FilePickerEvent
        }
    }

    /**
     * Settings-related events
     */
    sealed interface SettingsEvent : BusEvent {
        /**
         * Event that indicates a state change of the "Offline Mode" setting
         */
        data class OfflineModeChangedEvent(val enabled: Boolean) : BusEvent

        /**
         * Indicates the request to immediately schedule an update of the offline cache
         */
        data object ScheduleOfflineCacheUpdateRequestEvent : BusEvent

        /**
         * Event that indicates a state change of the "Theme" setting
         */
        data class ThemeChangedEvent(val theme: String) : BusEvent
    }

    sealed interface DebugEvent : BusEvent {
        /**
         * Event that indicates a state change of the network request logging
         */
        data class LogNetworkRequestsChangedEvent(val enabled: Boolean) : BusEvent
    }

    /**
     * CodeEditor specific events
     */
    sealed interface CodeEditorBusEvent : BusEvent {
        data class GoToDocument(val documentId: String) : CodeEditorBusEvent
        data class GoToSection(val sectionId: String) : CodeEditorBusEvent
        data class GoToResource(val resourceId: String) : CodeEditorBusEvent
    }
}
