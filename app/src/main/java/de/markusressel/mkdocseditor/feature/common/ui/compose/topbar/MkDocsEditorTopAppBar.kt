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
import de.markusressel.mkdocseditor.feature.editor.ui.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.ui.compose.ShowInBrowserAction

@Composable
fun MkDocsEditorTopAppBar(
    title: String,
    canGoBack: Boolean = true,
    onBackClicked: (() -> Unit)? = null,
    actions: List<TopAppBarAction> = emptyList(),
    onActionClicked: (TopAppBarAction) -> Unit = {},
) {
    val navigator = LocalNavigator.currentOrThrow

    val defaultNavigationAction: () -> Unit = { navigator.pop() }

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
                    is TopAppBarAction.ShowInBrowserAction -> {
                        ShowInBrowserAction(
                            modifier = Modifier.align(Alignment.CenterVertically),
                            onClick = { onActionClicked(action) }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = MaterialTheme.colorScheme.primary,
//                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
//                    actionIconContentColor = MaterialTheme.colorScheme.primary,
        )
    )
}
