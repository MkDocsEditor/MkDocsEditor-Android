package de.markusressel.mkdocseditor.feature.about.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
) {
    LibrariesContainer(
        modifier = modifier,
        header = {
            item {
                AboutHeader(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                )
            }
        },
        showAuthor = true,
        showVersion = true,
        showLicenseBadges = true,
        colors = LibraryDefaults.libraryColors(
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.background),
            badgeBackgroundColor = MaterialTheme.colorScheme.primary,
            badgeContentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.primary),
        ),
    )
}

