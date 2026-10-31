package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ValidateEditedBackendConfigUseCase @Inject constructor(
) {
    suspend operator fun invoke(
        name: String,
        description: String,
        domain: String,
        port: String,
    ): Boolean {
        return name.run {
            name.isNotBlank() &&
//                description.isNotBlank() &&
                domain.isNotBlank() &&
                (0..65535).contains(port.toIntOrNull() ?: -1)
        }
    }
}
