package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.DocumentData
import de.markusressel.mkdocseditor.feature.browser.data.ResourceData
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SearchUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    operator fun invoke(searchTerm: String): List<SearchResultItem> {
        return dataRepository.find(searchTerm).map {
            when (it) {
                is DocumentData -> SearchResultItem.Document(
                    documentId = it.id,
                    documentName = it.name,
                )

                is SectionData -> SearchResultItem.Section(
                    sectionId = it.id,
                    sectionName = it.name,
                )

                is ResourceData -> SearchResultItem.Resource(
                    resourceId = it.id,
                    resourceName = it.name,
                )

                else -> {
                    throw IllegalArgumentException("Unknown type: ${it::class.java}")
                }
            }
        }
    }
}
