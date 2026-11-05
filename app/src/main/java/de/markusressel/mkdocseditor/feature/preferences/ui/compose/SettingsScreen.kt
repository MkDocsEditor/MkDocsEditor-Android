package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.about.ui.compose.AboutScreen
import de.markusressel.mkdocseditor.feature.main.ui.compose.LocalMainScreenOnBack
import de.markusressel.mkdocseditor.feature.preferences.ui.PreferencesViewModel

/**
 * The settings list, the first screen of the settings tab
 */
internal object SettingsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val onBack = LocalMainScreenOnBack.current
        val viewModel = getViewModel<PreferencesViewModel>()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    PreferencesViewModel.Event.OpenAbout -> navigator.push(AboutScreen)
                }
            }
        }

        PreferencesScreen(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
            onBack = onBack,
        )
    }
}
