package de.markusressel.mkdocseditor.feature.main.ui.compose.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose.BackendConfigSelectionScreen
import de.markusressel.mkdocseditor.feature.main.data.rememberMainNavigationRequests

object BackendSelectionTab : Tab {
    override val options: TabOptions
        @Composable
        get() = remember {
            TabOptions(
                index = 0u,
                title = "",
                icon = null
            )
        }

    @Composable
    override fun Content() {
        val authFlowsFinished by rememberMainNavigationRequests().authFlowsFinished.collectAsState()
        var handledAuthFlows by rememberSaveable { mutableIntStateOf(0) }

        Navigator(BackendConfigSelectionScreen) { navigator ->
            // after a sign-in, this tab shows the server list again instead of a leftover auth screen
            LaunchedEffect(authFlowsFinished) {
                if (authFlowsFinished != handledAuthFlows) {
                    handledAuthFlows = authFlowsFinished
                    navigator.replaceAll(BackendConfigSelectionScreen)
                }
            }

            SlideTransition(navigator)
        }
    }
}