package de.markusressel.mkdocseditor.feature.about.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults

@Composable
fun AboutScreen() {
    LibrariesContainer(
        modifier = Modifier.fillMaxSize(),
        colors = LibraryDefaults.libraryColors(
            backgroundColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.background),
            badgeBackgroundColor = MaterialTheme.colorScheme.primary,
            badgeContentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.primary),
        ),
    )
}