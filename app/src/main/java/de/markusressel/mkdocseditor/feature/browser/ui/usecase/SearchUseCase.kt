package de.markusressel.mkdocseditor.feature.browser.ui.usecase

import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import javax.inject.Inject

class SearchUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    operator fun invoke(searchTerm: String): List<IdentifiableListItem> {
        return dataRepository.find(searchTerm)
    }
}
