package de.markusressel.mkdocseditor.feature.backendconfig.edit.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import javax.inject.Inject

internal class DeleteBackendConfigUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke(config: BackendConfig): Boolean {
        return backendConfigRepository.delete(config)
    }
}

