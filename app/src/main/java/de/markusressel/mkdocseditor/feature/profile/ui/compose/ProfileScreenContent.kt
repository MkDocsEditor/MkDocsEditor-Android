package de.markusressel.mkdocseditor.feature.profile.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.profile.ui.ProfileViewModel
import de.markusressel.mkdocseditor.feature.profile.ui.ProfileViewModel.UiEvent

@Composable
internal fun ProfileScreenContent(
    uiState: ProfileViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(id = R.string.screen_profile_title),
            )
        },
        floatingActionButton = {
            ExpandableFab(
                modifier = Modifier.fillMaxSize(),
                items = uiState.fabConfig.right,
                onItemClicked = {
                    onUiEvent(UiEvent.ExpandableFabItemSelected(item = it))
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            Text(text = "Active Profile: ${uiState.activeProfile?.name ?: "None"}")
        }
    }
}