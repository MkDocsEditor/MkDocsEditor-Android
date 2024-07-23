package de.markusressel.mkdocseditor.feature.common.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.markusressel.kutepreferences.ui.theme.KutePreferencesTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
fun TabContentScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        content = content
    )
}

@CombinedPreview
@Composable
private fun TabContentScaffoldPreview() {
    KutePreferencesTheme {
        TabContentScaffold(
            topBar = {
                TopAppBar(title = { Text("Title") })
            },
            bottomBar = {
                BottomAppBar {
                    Text(text = "Bottom bar")
                }
            },
            content = { paddingValues ->
                // Content
                Text(
                    modifier = Modifier.padding(paddingValues),
                    text = "Content"
                )
            }
        )
    }
}