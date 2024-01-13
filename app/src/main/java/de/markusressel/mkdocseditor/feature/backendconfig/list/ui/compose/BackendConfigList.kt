package de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.backendconfig.list.ui.BackendSelectionViewModel

@Composable
internal fun BackendConfigList(
    modifier: Modifier = Modifier,
    uiState: BackendSelectionViewModel.UiState,
    onUiEvent: (BackendSelectionViewModel.UiEvent) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uiState.listItems.forEach { item ->
            BackendConfigListItem(item = item, modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(), onClick = {
                onUiEvent(BackendSelectionViewModel.UiEvent.BackendConfigClicked(item))
            }, onLongClick = {
                onUiEvent(BackendSelectionViewModel.UiEvent.BackendConfigLongClicked(item))
            })
        }
    }
}