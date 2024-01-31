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
                        val (charsBefore, excerpt, charsAfter) = item.getInlineExcerpt(searchTerm)
                        SearchResultItem.Document(
                            documentId = item.id,
                            documentName = item.name,
                            documentExcerptData = SearchResultItem.Document.ExcerptData(
                                excerpt = excerpt,
                                charsBefore = charsBefore,
                                charsAfter = charsAfter,
                            ).takeIf { it.excerpt.isNotBlank() }
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

    private fun DocumentData.getInlineExcerpt(
        searchTerm: String,
        charsBeforeMatch: Int = 10,
        charsAfterMatch: Int = 50
    ): Triple<Int, String, Int> {
        val content = content?.text ?: ""
        val matchingCharIndex = content.indexOf(searchTerm)
        val startCharIndex = 0.coerceAtLeast(matchingCharIndex - charsBeforeMatch)
        val endCharIndex = (content.length - 1).coerceIn(0, matchingCharIndex + charsAfterMatch)
        val charsAfter = ((content.length - 1).coerceAtLeast(0) - endCharIndex).coerceAtLeast(0)
        return Triple(
            matchingCharIndex,
            content.substring(startCharIndex, endCharIndex),
            charsAfter
        )
    }

    private fun DocumentData.getExcerpt(
        searchTerm: String,
        wordsBeforeMatch: Int = 3,
        wordsAfterMatch: Int = 10
    ): String {
        val words = (content?.text ?: "").split(" ")
        val matchingWordIndex = words.indexOfFirst { it.contains(searchTerm) }
        val startWordIndex = 0.coerceAtLeast(matchingWordIndex - wordsBeforeMatch)
        val endWordIndex = (words.size - 1).coerceIn(0, matchingWordIndex + wordsAfterMatch)
        return words.subList(startWordIndex, endWordIndex).joinToString(" ")
    }

//    private fun DocumentData.getExcerpt(
//        searchTerm: String,
//        linesBeforeMatch: Int = 0,
//        linesAfterMatch: Int = 3
//    ): String {
//        val lines = (content?.text ?: "").split("\n")
//        val matchingLineIndex = lines.indexOfFirst { it.contains(searchTerm) }
//        val startLineIndex = 0.coerceAtLeast(matchingLineIndex - linesBeforeMatch)
//        val endLineIndex = (lines.size - 1).coerceIn(0, matchingLineIndex + linesAfterMatch)
//        return lines.subList(startLineIndex, endLineIndex).joinToString("\n")
//    }
}
