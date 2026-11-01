package de.markusressel.mkdocseditor.feature.search.domain

import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
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
            dataRepository.find(searchTerm).map { found ->
                when (val item = found.item) {
                    is DocumentData -> {
                        val matches = SearchExcerpts.find(item.content?.text.orEmpty(), searchTerm)
                        SearchResultItem.Document(
                            documentId = item.id,
                            documentName = item.name,
                            excerpts = matches.excerpts,
                            path = found.path,
                            modtime = item.modtime,
                            matchCount = matches.matchCount,
                        )
                    }

                    is SectionData -> SearchResultItem.Section(
                        sectionId = item.id,
                        sectionName = item.name,
                        path = found.path,
                        documentCount = item.documents.size,
                        sectionCount = item.subsections.size,
                    )

                    is ResourceData -> SearchResultItem.Resource(
                        resourceId = item.id,
                        resourceName = item.name,
                        path = found.path,
                        filesize = item.filesize,
                        modtime = item.modtime,
                    )

                    else -> {
                        throw IllegalArgumentException("Unknown type: ${item::class.java}")
                    }
                }
            }.sortedByRelevance(searchTerm)
        }
}
