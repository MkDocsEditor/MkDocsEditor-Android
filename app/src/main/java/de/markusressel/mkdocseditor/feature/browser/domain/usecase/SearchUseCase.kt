package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import javax.inject.Inject

internal class SearchUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    operator fun invoke(searchTerm: String): List<Any> {
        return dataRepository.find(searchTerm)
    }
}
