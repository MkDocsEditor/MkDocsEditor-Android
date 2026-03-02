package de.markusressel.mkdocseditor.feature.main.ui.compose.tab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import de.markusressel.mkdocseditor.feature.about.ui.compose.AboutScreen
import de.markusressel.mkdocseditor.feature.main.ui.compose.LocalMainScreenOnBack

object AboutTab : Tab {
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
        val onBack = LocalMainScreenOnBack.current
        BackHandler(
            enabled = true,
            onBack = onBack,
        )

        AboutScreen(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        )
    }
}