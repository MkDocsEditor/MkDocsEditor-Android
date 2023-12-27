package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import javax.inject.Inject


internal class ValidateBackendConfigUseCase @Inject constructor(
    private val getBackendConfigItemsUseCase: GetBackendConfigItemsUseCase,
) {
    suspend operator fun invoke(
        name: String,
        description: String,
        domain: String,
        port: String,
        currentUseSsl: Boolean,
        authConfig: AuthConfig?
    ): Boolean {
        return name.run {
            name.isNotBlank() &&
                description.isNotBlank() &&
                domain.isNotBlank() &&
                (0..65535).contains(port.toIntOrNull() ?: -1) &&
                authConfig?.run {
                    username.isNotBlank() &&
                        password.isNotBlank()
                } ?: true
        } && getBackendConfigItemsUseCase().none {
            it.name == name
        }
    }
}
