package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendAuthConfig
import javax.inject.Inject

internal class GetBackendAuthConfigsUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke() = backendConfigRepository.getAuthConfigs().map {
        it.toBackendAuthConfig()
    }
}

