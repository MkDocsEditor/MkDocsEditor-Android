package de.markusressel.mkdocseditor.feature.backendconfig.list.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.BackendManager
import javax.inject.Inject

internal class SelectBackendConfigUseCase @Inject constructor(
    private val backendManager: BackendManager,
) {
    suspend operator fun invoke(config: BackendConfig) {
        backendManager.setBackend(config)
    }
}