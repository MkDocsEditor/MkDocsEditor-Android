package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel

data class BackendConfigEditScreen(
    val id: Long? = null
) : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current

        val viewModel: BackendConfigEditViewModel = hiltViewModel()
        LaunchedEffect(viewModel, id) {
            viewModel.initialize(id)
        }

        LaunchedEffect(viewModel.events) {
            viewModel.events.collect { event ->
                when (event) {
                    is BackendConfigEditViewModel.BackendEditEvent.CloseScreen -> {
                        navigator?.pop()
                    }

                    is BackendConfigEditViewModel.BackendEditEvent.Error -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }

                    is BackendConfigEditViewModel.BackendEditEvent.Info -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val uiState by viewModel.uiState.collectAsState()

//    BackHandler(
//        enabled = uiState.canGoUp,
//        onBack = {
//            val consumed = viewModel.navigateUp()
//            if (consumed.not()) {
//                onBack()
//            }
//        },
//    )

        BackendConfigEditScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent
        )
    }
}
