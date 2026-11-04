package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import de.markusressel.kutepreferences.ui.theme.KutePreferencesThemeDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.kutepreferences.ui.theme.KutePreferencesTheme
import de.markusressel.kutepreferences.ui.views.search.KutePreferencesScreen
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
        val defaults = KutePreferencesThemeDefaults.defaultColors
        KutePreferencesTheme(
            // flat and with the text colors of the other lists of the app
            colors = defaults.copy(
                category = defaults.category.copy(
                    elevation = 0.dp,
                    cardBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleColor = MaterialTheme.colorScheme.onSurface,
                ),
                section = defaults.section.copy(
                    elevation = 0.dp,
                    titleBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ),
        ) {
            KutePreferencesScreen(
                modifier = Modifier.fillMaxSize(),
                kuteViewModel = viewModel,
                contentPadding = PaddingValues(),
            )
        }
    }
}
