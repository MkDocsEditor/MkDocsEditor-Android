package de.markusressel.mkdocseditor.feature.dashboard.ui.compose

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.auth.account.ui.compose.AccountScreen
import de.markusressel.mkdocseditor.feature.dashboard.ui.DashboardEvent
import de.markusressel.mkdocseditor.feature.dashboard.ui.DashboardViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import de.markusressel.mkdocseditor.feature.main.ui.compose.LocalMainScreenOnBack
import de.markusressel.mkdocseditor.feature.main.ui.compose.LocalMainScreenStartTab
import de.markusressel.mkdocseditor.feature.search.ui.compose.SearchScreen

internal object DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getViewModel<DashboardViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        // when the file list is the start page, back goes there first
        val onBack = LocalMainScreenOnBack.current
        BackHandler(enabled = LocalMainScreenStartTab.current != NavItem.Dashboard, onBack = onBack)

        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            // only while visible: nothing needs the socket in the background
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.followServerEvents() }
        }

        // the answer needs no handling: the next check simply finds the permission or not
        val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is DashboardEvent.OpenDocument -> navigator.push(CodeEditorScreen(documentId = event.documentId))
                    is DashboardEvent.OpenAccount -> navigator.push(AccountScreen(backendId = event.backendId))
                    DashboardEvent.OpenSearch -> navigator.push(SearchScreen)
                    is DashboardEvent.Toast -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                    DashboardEvent.AskForNotifications -> askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        DashboardScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent,
            previewResourceLoader = viewModel.previewResourceLoader,
        )
    }
}
