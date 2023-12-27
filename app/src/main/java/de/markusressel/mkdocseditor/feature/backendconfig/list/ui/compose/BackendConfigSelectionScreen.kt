package de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.BackendConfigEditScreen
import de.markusressel.mkdocseditor.feature.backendconfig.list.ui.BackendSelectionViewModel

object BackendConfigSelectionScreen : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val viewModel: BackendSelectionViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is BackendSelectionViewModel.BackendSelectionEvent.CreateBackend -> {
                        navigator.push(BackendConfigEditScreen())
                    }

                    is BackendSelectionViewModel.BackendSelectionEvent.EditBackend -> {
                        navigator.push(BackendConfigEditScreen(event.id))
                    }

                    is BackendSelectionViewModel.BackendSelectionEvent.Error -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        BackendSelectionScreenContent(
            modifier = Modifier.fillMaxSize(),
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent
        )
    }
}
