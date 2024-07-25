package de.markusressel.mkdocseditor.feature.main.ui.compose


import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.FileBrowserScreen

@Composable
internal fun MkDocsEditorListOnlyContent(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Navigator(FileBrowserScreen) { navigator ->
            SlideTransition(navigator)
        }
    }
}
