package de.markusressel.mkdocseditor.feature.main.ui.compose.tab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import de.markusressel.mkdocseditor.feature.main.ui.compose.MkDocsEditorListOnlyContent

/**
 * The same tab and the same layout for every window size. A rotation changes the window size, and the
 * file browser's navigator, with the documents open in it, is only restored at the same place in the
 * composition.
 */
object FileBrowserTab : Tab {
    override val options: TabOptions
        @Composable
        get() = remember {
            TabOptions(
                index = 0u,
                title = "",
                icon = null
            )
        }

    @Composable
    override fun Content() {
        MkDocsEditorListOnlyContent(
            modifier = Modifier.fillMaxSize(),
        )
    }
}
