package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
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
        authConfig: AuthConfig?,
    ): Boolean {
        return name.run {
            name.isNotBlank() &&
//                description.isNotBlank() &&
                domain.isNotBlank() &&
                (0..65535).contains(port.toIntOrNull() ?: -1) &&
                authConfig?.run {
                    username.isNotBlank() &&
                        password.isNotBlank()
                } ?: true
        }
    }
}
