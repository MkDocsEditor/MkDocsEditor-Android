package de.markusressel.mkdocseditor.feature.filepicker.ui.compose

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.filepicker.ui.FilePickerViewModel

internal data class FilePickerScreen(
    private val mimeTypeFilter: String = "*/*",
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = getViewModel<FilePickerViewModel>()

        FilePickerComponent(
            onResult = {
                viewModel.onUiEvent(FilePickerViewModel.UiEvent.FilePickerResult(it))
                navigator.pop()
            },
            mimeTypeFilter = mimeTypeFilter
        )
    }
}
