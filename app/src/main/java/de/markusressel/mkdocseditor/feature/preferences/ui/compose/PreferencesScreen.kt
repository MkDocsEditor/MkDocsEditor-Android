package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.kutepreferences.ui.theme.KuteColors
import de.markusressel.kutepreferences.ui.theme.KutePreferencesTheme
import de.markusressel.kutepreferences.ui.theme.KutePreferencesThemeDefaults
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
        KutePreferencesTheme(colors = settingsColors()) {
            KutePreferencesScreen(
                modifier = Modifier.fillMaxSize(),
                kuteViewModel = viewModel,
                contentPadding = PaddingValues(),
            )
        }
    }
}

/**
 * The settings look like the cards of the file browser and the dashboard: elevated cards, titles in the text color of
 * the other lists, and section titles like the ones on the dashboard.
 */
@Composable
internal fun settingsColors(): KuteColors {
    val defaults = KutePreferencesThemeDefaults.defaultColors
    val cardColor = CardDefaults.elevatedCardColors().containerColor
    return defaults.copy(
        category = defaults.category.copy(
            elevation = CARD_ELEVATION,
            cardBackgroundColor = cardColor,
            titleColor = MaterialTheme.colorScheme.onSurface,
            shape = CardDefaults.elevatedShape,
        ),
        section = defaults.section.copy(
            elevation = CARD_ELEVATION,
            titleBackgroundColor = cardColor,
            contentBackgroundColor = cardColor,
            titleTextStyle = MaterialTheme.typography.titleMedium,
            shape = CardDefaults.elevatedShape,
        ),
        defaultItem = defaults.defaultItem.copy(
            titleColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

// as the entries of the file browser
private val CARD_ELEVATION = 2.dp
