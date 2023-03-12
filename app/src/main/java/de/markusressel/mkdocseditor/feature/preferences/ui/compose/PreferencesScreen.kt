package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.kutepreferences.ui.theme.CategoryTheme
import de.markusressel.kutepreferences.ui.theme.DefaultItemTheme
import de.markusressel.kutepreferences.ui.theme.KuteColors
import de.markusressel.kutepreferences.ui.theme.KutePreferencesTheme
import de.markusressel.kutepreferences.ui.theme.SearchBarTheme
import de.markusressel.kutepreferences.ui.theme.SectionTheme
import de.markusressel.kutepreferences.ui.views.KuteOverview
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
        KutePreferencesTheme(
            colors = KuteColors(
                searchBar = SearchBarTheme(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    textColor = Color.White.copy(alpha = 0.87f),
                    hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                section = SectionTheme(
                    titleBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleTextColor = MaterialTheme.colorScheme.primary,
                    contentBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                category = CategoryTheme(
                    cardBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleColor = MaterialTheme.colorScheme.primary,
                    subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                defaultItem = DefaultItemTheme(
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        ) {
            val currentItems by viewModel.currentPreferenceItems.collectAsState(initial = emptyList())

            KuteOverview(
                modifier = Modifier.fillMaxSize(),
                items = currentItems
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}