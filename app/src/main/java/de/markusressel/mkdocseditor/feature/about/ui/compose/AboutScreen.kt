package de.markusressel.mkdocseditor.feature.about.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.chipColors
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.about.data.appVersionText
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar

/**
 * The about page, opened from the settings
 */
object AboutScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        TabContentScaffold(
            topBar = {
                MkDocsEditorTopAppBar(
                    title = stringResource(R.string.about_title),
                    canGoBack = true,
                    onBackClicked = { navigator.pop() },
                )
            },
        ) { paddingValues ->
            AboutContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

/**
 * The app, its version and the libraries it uses
 */
@Composable
fun AboutContent(
    modifier: Modifier = Modifier,
) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val context = LocalContext.current
    val version = remember(context) { context.appVersionText() }

    val chipColors = LibraryDefaults.chipColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.background),
    )

    LibrariesContainer(
        modifier = modifier,
        libraries = libraries,
        header = {
            item {
                AboutHeader(
                    version = version,
                    modifier = Modifier.padding(8.dp)
                )
            }
        },
        showAuthor = true,
        showVersion = true,
        showLicenseBadges = true,
        colors = LibraryDefaults.libraryColors(
            versionChipColors = chipColors,
            licenseChipColors = chipColors,
            fundingChipColors = chipColors,
        ),
    )
}
