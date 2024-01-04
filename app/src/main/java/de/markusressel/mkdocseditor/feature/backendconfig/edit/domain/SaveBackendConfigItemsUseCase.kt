package de.markusressel.mkdocseditor.feature.backendconfig.edit.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SaveBackendConfigItemsUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke(config: BackendConfig): Long {
        config.serverConfig?.let {
            backendConfigRepository.addOrUpdate(it)
        }
        return backendConfigRepository.addOrUpdate(config)
    }
}

