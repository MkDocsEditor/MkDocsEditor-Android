package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataFactory
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FindParentSectionOfResourceUseCase @Inject constructor(
    private val dataFactory: DataFactory,
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(resourceId: String): SectionData? {
        return dataRepository.getResource(resourceId).first().data?.parentSection?.target?.let {
            dataFactory.toSectionData(it)
        }
    }
}