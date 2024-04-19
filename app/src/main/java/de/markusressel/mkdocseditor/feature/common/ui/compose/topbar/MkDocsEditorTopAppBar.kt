package de.markusressel.mkdocseditor.feature.common.ui.compose.topbar

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.extensions.common.android.isComposePreview
import de.markusressel.mkdocseditor.feature.editor.ui.compose.ShowInBrowserAction
import de.markusressel.mkdocseditor.feature.editor.ui.compose.TogglePreviewAction

@Composable
fun MkDocsEditorTopAppBar(
    title: String,
    canGoBack: Boolean = true,
    onBackClicked: (() -> Unit)? = null,
) {
    MkDocsEditorTopAppBar<TopAppBarAction>(
        title = title,
        canGoBack = canGoBack,
        onBackClicked = onBackClicked,
    )
}

@Composable
fun <T : TopAppBarAction> MkDocsEditorTopAppBar(
    title: String,
    canGoBack: Boolean = true,
    onBackClicked: (() -> Unit)? = null,
    actions: List<T> = emptyList(),
    onActionClicked: (T) -> Unit = {},
) {
    val defaultNavigationAction: () -> Unit = if (isComposePreview().not()) {
        val navigator = LocalNavigator.currentOrThrow
        { navigator.pop() }
    } else {
        {}
    }

    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBackClicked ?: defaultNavigationAction) {
                    Image(
                        asset = MaterialDesignIconic.Icon.gmi_arrow_back,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        },
        actions = {
            actions.forEach { action ->
                when (action) {
                    is TopAppBarAction.FileBrowser.Search -> {
                        SearchAction(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            onClick = { onActionClicked(action) }
                        )
                    }

                    is TopAppBarAction.CodeEditor.TogglePreviewAction -> {
                        TogglePreviewAction(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            orientation = action.orientation,
                            onClick = { onActionClicked(action) }
                        )
                    }
                    is TopAppBarAction.CodeEditor.ShowInBrowserAction -> {
                        ShowInBrowserAction(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            onClick = { onActionClicked(action) }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
//                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
//                    actionIconContentColor = MaterialTheme.colorScheme.primary,
        )
    )
}
