package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FindParentSectionOfResourceUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(resourceId: String): SectionEntity? {
        return dataRepository.getResource(resourceId).first().data?.parentSection?.target
    }
}