package de.markusressel.mkdocseditor.feature.backendconfig.list.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import javax.inject.Inject

internal class SelectBackendConfigUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke(config: BackendConfig) {
        backendConfigRepository.selectBackendConfig(config)
    }
}