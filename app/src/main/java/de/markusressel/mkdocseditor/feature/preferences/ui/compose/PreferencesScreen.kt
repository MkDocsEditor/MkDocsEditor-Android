package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
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
            colors = when {
                isSystemInDarkTheme() -> KuteColors(
                    searchBar = SearchBarTheme(
                        backgroundColor = Color.White.copy(alpha = 0.1f),
                        textColor = Color.White.copy(alpha = 0.87f),
                        hintColor = Color.White.copy(alpha = 0.6f),
                        iconColor = Color.White.copy(alpha = 0.6f),
                    ),
                    section = SectionTheme(
                        titleTextColor = Color.White.copy(alpha = 0.87f),
                        titleBackgroundColor = MaterialTheme.colorScheme.surface,
                        contentBackgroundColor = Color.White.copy(alpha = 0.1f),
                    ),
                    category = CategoryTheme(
                        cardBackgroundColor = Color.White.copy(alpha = 0.1f),
                        titleColor = Color.White.copy(alpha = 0.87f),
                        subtitleColor = Color.White.copy(alpha = 0.87f),
                        iconColor = Color.White.copy(alpha = 0.87f),
                    ),
                    defaultItem = DefaultItemTheme(
                        titleColor = Color.White.copy(alpha = 0.87f),
                        subtitleColor = Color.White.copy(alpha = 0.87f),
                        iconColor = Color.White.copy(alpha = 0.87f),
                    ),
                )
                else -> KuteColors(
                    searchBar = SearchBarTheme(
                        backgroundColor = Color.White,
                        textColor = Color.Black.copy(alpha = 0.87f),
                        hintColor = Color.Black.copy(alpha = 0.6f),
                        iconColor = Color.Black.copy(alpha = 0.6f),
                    ),
                    section = SectionTheme(
                        titleTextColor = Color.Black.copy(alpha = 0.87f),
                        titleBackgroundColor = Color.White,
                        contentBackgroundColor = Color.White,
                    ),
                    category = CategoryTheme(
                        cardBackgroundColor = Color.White,
                        titleColor = Color.Black.copy(alpha = 0.87f),
                        subtitleColor = Color.Black.copy(alpha = 0.6f),
                        iconColor = Color.Black.copy(alpha = 0.87f),
                    ),
                    defaultItem = DefaultItemTheme(
                        titleColor = Color.Black.copy(alpha = 0.87f),
                        subtitleColor = Color.Black.copy(alpha = 0.6f),
                        iconColor = Color.Black.copy(alpha = 0.87f),
                    ),
                )
            }
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