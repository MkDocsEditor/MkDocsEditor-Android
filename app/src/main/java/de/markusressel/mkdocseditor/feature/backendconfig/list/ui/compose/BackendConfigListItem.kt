package de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.displayAddress

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BackendConfigListItem(
    item: BackendConfig,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onEditClick)
            .then(modifier),
        colors = if (item.isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = item.isSelected, onClick = onClick)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            ) {
                Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                if (item.description.isNotBlank()) {
                    Text(text = item.description, style = MaterialTheme.typography.bodySmall)
                }
                item.serverConfig?.let { server ->
                    Text(
                        text = displayAddress(server.domain, server.port, server.useSsl),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.server_edit_title),
                )
            }
        }
    }
}
