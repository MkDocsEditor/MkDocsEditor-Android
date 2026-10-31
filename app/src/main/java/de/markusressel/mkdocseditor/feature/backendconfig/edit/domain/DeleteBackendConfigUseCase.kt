package de.markusressel.mkdocseditor.feature.backendconfig.edit.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DeleteBackendConfigUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(config: BackendConfig): Boolean {
        // end the session on the server too, if it is reachable
        config.toBackendRef()?.let { authRepository.signOut(it) } ?: authRepository.forget(config.id)
        return backendConfigRepository.delete(config)
    }
}

