package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DrawerValue
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.markusressel.mkdocseditor.ui.activity.MainViewModel
import de.markusressel.mkdocseditor.ui.activity.UiEvent
import de.markusressel.mkdocseditor.ui.activity.UiState

@Composable
internal fun MainScreen(
    mainViewModel: MainViewModel
) {
    val uiState by mainViewModel.uiState.collectAsState()

    MainScreenContent(
        uiState = uiState,
        onUiEvent = mainViewModel::onUiEvent
    )
}

@Preview
@Composable
private fun MainScreenContentPreview() {
    MainScreenContent(
        uiState = UiState(),
        onUiEvent = {}
    )
}

@Composable
private fun MainScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(title = { Text("Top App Bar") }, backgroundColor = MaterialTheme.colors.primary)
        },
        drawerContent = {

        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {

            }
        },
        bottomBar = {
            BottomBar(
                selectedNavItem = uiState.selectedBottomBarItem,
                navItems = uiState.bottomBarNavItems,
                onItemSelected = { onUiEvent(UiEvent.BottomNavItemSelected(it)) }
            )
        }
    )
}