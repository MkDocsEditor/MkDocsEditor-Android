package de.markusressel.mkdocseditor.feature.backendconfig.edit.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AddAuthConfigUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke(config: AuthConfig): Long {
        return backendConfigRepository.addOrUpdate(config)
    }
}

