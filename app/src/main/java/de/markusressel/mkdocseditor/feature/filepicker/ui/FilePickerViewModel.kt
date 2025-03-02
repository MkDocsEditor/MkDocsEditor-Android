package de.markusressel.mkdocseditor.feature.filepicker.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import javax.inject.Inject

@HiltViewModel
internal class FilePickerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val eventBusManager: EventBusManager,
) : ViewModel() {

    fun onUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.FilePickerResult -> {
                eventBusManager.send(BusEvent.FeatureEvent.FilePickerEvent.FilePickerResult(event.uri))
            }
        }
    }

    sealed class UiEvent {
        data class FilePickerResult(val uri: Uri?) : UiEvent()
    }
}
