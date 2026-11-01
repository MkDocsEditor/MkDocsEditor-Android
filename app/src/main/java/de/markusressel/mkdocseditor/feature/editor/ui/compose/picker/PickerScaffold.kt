package de.markusressel.mkdocseditor.feature.editor.ui.compose.picker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.markusressel.mkdocseditor.R

/**
 * A full screen dialog with a title, a close button and tabs, for the pickers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerScaffold(
    title: String,
    tabs: List<Pair<String, String>>,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("picker-close")) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.picker_close))
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)) {
                    tabs.forEach { (id, label) ->
                        Tab(
                            selected = id == selectedTab,
                            onClick = { onTabSelected(id) },
                            text = { Text(label) },
                            modifier = Modifier.testTag("tab-$id"),
                        )
                    }
                }
                content()
            }
        }
    }
}
