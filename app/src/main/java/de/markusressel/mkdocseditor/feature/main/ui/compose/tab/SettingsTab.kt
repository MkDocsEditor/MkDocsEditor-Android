package de.markusressel.mkdocseditor.feature.main.ui.compose.tab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import de.markusressel.mkdocseditor.feature.preferences.ui.compose.PreferencesScreen

object SettingsTab : Tab {
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
        PreferencesScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = {
                // FIXME:
//                onUiEvent(UiEvent.BottomNavItemSelected(NavItem.FileBrowser))
            }
        )
    }
}