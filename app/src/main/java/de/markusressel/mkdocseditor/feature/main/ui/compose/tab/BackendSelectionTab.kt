package de.markusressel.mkdocseditor.feature.main.ui.compose.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose.BackendConfigSelectionScreen

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
        Navigator(BackendConfigSelectionScreen) { navigator ->
            SlideTransition(navigator)
        }
    }
}