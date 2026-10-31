package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

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
    ): Boolean {
        return name.run {
            name.isNotBlank() &&
                domain.isNotBlank() &&
                (0..65535).contains(port.toIntOrNull() ?: -1)
        } && getBackendConfigsUseCase().none {
            it.name == name
        }
    }
}
