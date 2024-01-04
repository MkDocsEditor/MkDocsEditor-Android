package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
internal class ValidateNewBackendConfigUseCase @Inject constructor(
    private val getBackendConfigsUseCase: GetBackendConfigsUseCase,
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
                domain.isNotBlank() &&
                (0..65535).contains(port.toIntOrNull() ?: -1) &&
                authConfig?.run {
                    username.isNotBlank() &&
                        password.isNotBlank()
                } ?: true
        } && getBackendConfigsUseCase().none {
            it.name == name
        }
    }
}
