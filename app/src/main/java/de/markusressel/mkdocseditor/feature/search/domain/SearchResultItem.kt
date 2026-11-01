package de.markusressel.mkdocseditor.feature.search.domain

import java.util.Date

sealed class SearchResultItem {
    abstract val name: String

    /** the names of the folders it is in, without the root */
    abstract val path: List<String>

    data class Document(
        val documentId: String,
        val documentName: String,
        val excerpts: List<ExcerptData> = emptyList(),
        override val path: List<String> = emptyList(),
        val modtime: Date? = null,
        /** matches in the content, which can be more than [excerpts] */
        val matchCount: Int = 0,
    ) : SearchResultItem() {
        override val name get() = documentName

        /**
         * A line of the page around the first match in it
         *
         * @param matches the matches, as indices of [text]
         * @param heading the heading this line is below
         * @param truncatedStart the line goes on before [text]
         * @param truncatedEnd the line goes on after [text]
         */
        data class ExcerptData(
            val text: String,
            val matches: List<IntRange> = emptyList(),
            val heading: String? = null,
            val truncatedStart: Boolean = false,
            val truncatedEnd: Boolean = false,
        )
    }

    data class Section(
        val sectionId: String,
        val sectionName: String,
        override val path: List<String> = emptyList(),
        val documentCount: Int = 0,
        val sectionCount: Int = 0,
    ) : SearchResultItem() {
        override val name get() = sectionName
    }

    data class Resource(
        val resourceId: String,
        val resourceName: String,
        override val path: List<String> = emptyList(),
        val filesize: Long = -1,
        val modtime: Date? = null,
    ) : SearchResultItem() {
        override val name get() = resourceName
    }
}
