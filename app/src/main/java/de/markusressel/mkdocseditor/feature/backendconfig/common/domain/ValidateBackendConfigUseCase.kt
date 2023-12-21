package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import javax.inject.Inject


internal class ValidateBackendConfigUseCase @Inject constructor(
) {
    suspend operator fun invoke(config: BackendConfig): Boolean {
        return true
    }
}
