package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.kutepreferences.ui.theme.KutePreferencesTheme
import de.markusressel.kutepreferences.ui.views.KuteOverview
import de.markusressel.mkdocseditor.feature.preferences.ui.PreferencesViewModel

@Composable
internal fun PreferencesScreen(
    viewModel: PreferencesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        KutePreferencesTheme {
            val currentItems by viewModel.currentPreferenceItems.collectAsState(initial = emptyList())

            KuteOverview(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                items = currentItems
            )
        }
    }
}