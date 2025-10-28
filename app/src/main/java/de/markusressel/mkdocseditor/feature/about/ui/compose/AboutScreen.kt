package de.markusressel.mkdocseditor.feature.about.ui.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.chipColors
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import de.markusressel.mkdocseditor.R

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
) {
    val libraries by produceLibraries(R.raw.aboutlibraries)

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

