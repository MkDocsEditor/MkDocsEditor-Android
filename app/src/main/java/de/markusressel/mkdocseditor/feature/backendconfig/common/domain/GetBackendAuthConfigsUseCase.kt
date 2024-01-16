package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.data.persistence.entity.UserPasswordAuthConfigEntity
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toAuthConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetBackendAuthConfigsUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke() = backendConfigRepository.getAuthConfigs().map(
        UserPasswordAuthConfigEntity::toAuthConfig
    )
}

