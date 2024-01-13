package de.markusressel.mkdocseditor.feature.filepicker.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.eightbitlab.rxbus.Bus
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.event.BusEvent
import javax.inject.Inject

@HiltViewModel
internal class FilePickerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    fun onUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.FilePickerResult -> {
                Bus.send(BusEvent.FilePickerResult(event.uri))
            }
        }
    }

    sealed class UiEvent {
        data class FilePickerResult(val uri: Uri?) : UiEvent()
    }
}
