package de.markusressel.mkdocseditor.feature.browser.data

import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel

data class SectionBackstackItem(val sectionId: String)

val ROOT_SECTION = SectionBackstackItem(FileBrowserViewModel.ROOT_SECTION_ID)