package de.markusressel.mkdocseditor.feature.backendconfig.edit.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        return withContext(Dispatchers.IO) { backendConfigRepository.delete(config) }
    }

    /** @return how many pages hold edits that removing this server would lose */
    suspend fun unsyncedPages(config: BackendConfig): Int =
        withContext(Dispatchers.IO) { backendConfigRepository.unsyncedPages(config) }
}

