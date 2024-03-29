package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.kutepreferences.ui.theme.KutePreferencesTheme
import de.markusressel.kutepreferences.ui.views.KutePreferencesScreen
import de.markusressel.mkdocseditor.feature.preferences.ui.PreferencesViewModel

@Composable
internal fun PreferencesScreen(
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    BackHandler(
        enabled = true,
        onBack = {
            val consumed = viewModel.navigateUp()
            if (consumed.not()) {
                onBack()
            }
        },
    )

    Box(modifier = modifier) {
        KutePreferencesTheme {
            KutePreferencesScreen(
                modifier = Modifier.fillMaxSize(),
                kuteViewModel = viewModel
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}