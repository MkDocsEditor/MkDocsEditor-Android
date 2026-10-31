package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.LoginHintStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Older versions stored the username and password of the backend API in plain text and
 * sent them with HTTP Basic auth. The backend now hands out sessions instead, so these
 * credentials are deleted; the username is kept to prefill the login.
 *
 * Only the credentials are touched: the offline cache and the local Automerge copies of
 * documents (with edits not synced yet) stay, and sync again after the login.
 */
@Singleton
internal class MigrateBackendBasicAuthUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository,
    private val loginHintStore: LoginHintStore,
) {
    operator fun invoke() {
        try {
            backendConfigRepository.removeBackendBasicAuth { backendId, username ->
                Timber.i("Removed the stored password of backend $backendId, sign in again")
                if (username.isNotBlank()) {
                    loginHintStore.setUsername(backendId, username)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not remove the stored backend passwords")
        }
    }
}
