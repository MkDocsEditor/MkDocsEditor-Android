package de.markusressel.mkdocseditor.feature.main.ui.compose.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.DashboardScreen

object DashboardTab : Tab {
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
        // pages open on top of the dashboard
        Box(modifier = Modifier.fillMaxSize()) {
            Navigator(DashboardScreen) { navigator ->
                SlideTransition(navigator)
            }
        }
    }
}
