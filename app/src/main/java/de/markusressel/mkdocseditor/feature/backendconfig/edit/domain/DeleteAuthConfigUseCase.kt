package de.markusressel.mkdocseditor.feature.backendconfig.edit.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DeleteAuthConfigUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke(id: Long): Boolean {
        return backendConfigRepository.deleteAuthConfig(id)
    }
}

