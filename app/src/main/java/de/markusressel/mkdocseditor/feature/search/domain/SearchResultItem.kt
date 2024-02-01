package de.markusressel.mkdocseditor.feature.search.domain

import androidx.compose.ui.text.AnnotatedString

sealed class SearchResultItem {
    data class Document(
        val documentId: String,
        val documentName: String,
        val excerpts: List<ExcerptData> = emptyList(),
    ) : SearchResultItem() {
        data class ExcerptData(
            val excerpt: AnnotatedString,
            val charsBefore: Int,
            val charsAfter: Int,
        )
    }

    data class Section(
        val sectionId: String,
        val sectionName: String,
    ) : SearchResultItem()

    data class Resource(
        val resourceId: String,
        val resourceName: String,
    ) : SearchResultItem()
}
