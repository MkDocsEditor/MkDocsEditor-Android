package de.markusressel.mkdocseditor.feature.filebrowser.data

import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserViewModel

data class SectionBackstackItem(val sectionId: String, val sectionName: String?)

val ROOT_SECTION = SectionBackstackItem(
    sectionId = FileBrowserViewModel.ROOT_SECTION_ID,
    sectionName = "/"
)