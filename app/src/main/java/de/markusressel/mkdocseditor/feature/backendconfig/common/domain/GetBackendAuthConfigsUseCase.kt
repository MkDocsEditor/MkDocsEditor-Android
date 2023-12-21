package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.data.persistence.entity.BackendAuthConfigEntity
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendAuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import javax.inject.Inject

internal class GetBackendAuthConfigsUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke(): List<BackendAuthConfig> {
        return backendConfigRepository.getAuthConfigs().map {
            it.toBackendAuthConfig()
        }
    }

    private fun BackendAuthConfigEntity.toBackendAuthConfig() = BackendAuthConfig(
        username = username,
        password = password,
    )
}

