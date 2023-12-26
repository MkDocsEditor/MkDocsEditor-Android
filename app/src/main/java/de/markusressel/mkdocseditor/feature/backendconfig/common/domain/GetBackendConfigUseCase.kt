package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendConfig
import javax.inject.Inject

internal class GetBackendConfigUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke(id: Long) =
        backendConfigRepository.getBackendConfig(id)?.toBackendConfig()
}

