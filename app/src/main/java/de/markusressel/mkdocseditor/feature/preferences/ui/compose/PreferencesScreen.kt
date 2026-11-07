package de.markusressel.mkdocseditor.feature.preferences.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.kutepreferences.ui.theme.KuteColors
import de.markusressel.kutepreferences.ui.theme.KutePreferencesTheme
import de.markusressel.kutepreferences.ui.theme.KutePreferencesThemeDefaults
import de.markusressel.kutepreferences.ui.views.search.KutePreferencesScreen
import de.markusressel.mkdocseditor.feature.common.ui.compose.horizontalBarsAndCutoutPadding
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

    val uiState by viewModel.preferencesUiState.collectAsStateWithLifecycle()
    // an open search covers the whole screen, like the search of the file browser
    val showTopBar = !uiState.isSearchActive

    Column(modifier = modifier) {
        if (showTopBar) {
            MkDocsEditorTopAppBar(
                title = stringResource(R.string.screen_settings_title),
                canGoBack = false,
            )
        }

        // without the top bar, the search bar of the preferences makes room for the status bar; in landscape the
        // navigation bar or the cutout can be at either side
        Box(
            modifier = Modifier
                .weight(1F)
                .then(if (showTopBar) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)
                .horizontalBarsAndCutoutPadding(),
        ) {
            KutePreferencesTheme(colors = settingsColors()) {
                KutePreferencesScreen(
                    modifier = Modifier.fillMaxSize(),
                    kuteViewModel = viewModel,
                    contentPadding = PaddingValues(),
                )
            }
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
