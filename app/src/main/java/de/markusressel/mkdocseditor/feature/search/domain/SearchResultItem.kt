package de.markusressel.mkdocseditor.feature.search.domain

sealed class SearchResultItem {
    data class Document(
        val documentId: String,
        val documentName: String,
        val documentExcerptData: ExcerptData?,
    ) : SearchResultItem() {
        data class ExcerptData(
            val excerpt: String,
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
