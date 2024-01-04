package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetBackendConfigsUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke(): List<BackendConfig> =
        backendConfigRepository.getBackendConfigs().map {
            it.toBackendConfig()
        }
}

