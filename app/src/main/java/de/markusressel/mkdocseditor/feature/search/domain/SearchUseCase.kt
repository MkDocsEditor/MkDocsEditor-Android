package de.markusressel.mkdocseditor.feature.search.domain

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.DocumentData
import de.markusressel.mkdocseditor.feature.browser.data.ResourceData
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SearchUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(searchTerm: String): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            dataRepository.find(searchTerm).map { item ->
                when (item) {
                    is DocumentData -> {
                        val excerpts =
                            item.getInlineExcerpts(searchTerm).filter { it.excerpt.isNotBlank() }
                        SearchResultItem.Document(
                            documentId = item.id,
                            documentName = item.name,
                            excerpts = excerpts
                        )
                    }

                    is SectionData -> SearchResultItem.Section(
                        sectionId = item.id,
                        sectionName = item.name,
                    )

                    is ResourceData -> SearchResultItem.Resource(
                        resourceId = item.id,
                        resourceName = item.name,
                    )

                    else -> {
                        throw IllegalArgumentException("Unknown type: ${item::class.java}")
                    }
                }
            }
        }

    private fun DocumentData.getInlineExcerpts(
        searchTerm: String,
        charsBeforeMatch: Int = 20,
        charsAfterMatch: Int = 80
    ): List<SearchResultItem.Document.ExcerptData> {
        val content = content?.text ?: ""
        val matches = searchTerm.toRegex(RegexOption.LITERAL).findAll(content)

        // TODO: skip match if it is already part of a previous excerpt
        //  or alternatively merge excerpts that are close to each other

        return matches.map { match ->
            val (startIndex, endIndex) = match.range.first to match.range.last

            val excerptStartIndex = (startIndex - charsBeforeMatch).coerceAtLeast(0)
            val excerptEndIndex =
                (endIndex + charsAfterMatch).coerceIn(0, (content.length - 1).coerceAtLeast(0))
            val charsAfterExcerptEntIndex =
                ((content.length - 1).coerceAtLeast(0) - excerptEndIndex).coerceAtLeast(0)
            SearchResultItem.Document.ExcerptData(
                excerpt = content.substring(excerptStartIndex, excerptEndIndex),
                charsBefore = excerptStartIndex,
                charsAfter = charsAfterExcerptEntIndex
            )
        }.toList()
    }
}
