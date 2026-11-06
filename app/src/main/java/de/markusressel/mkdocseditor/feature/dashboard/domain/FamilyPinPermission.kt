package de.markusressel.mkdocseditor.feature.dashboard.domain

import de.markusressel.mkdocseditor.auth.api.AccountRole
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Whether the signed-in account may change the pins for everyone: editors and admins may, readers may not. The role
 * is remembered per server and account, so this works offline. While it is unknown the answer is yes, and the server
 * decides.
 */
internal class FamilyPinPermission @Inject constructor(
    private val authRepository: AuthRepository,
    private val backends: AuthBackends,
) {
    /** @return what the last known role allows, null when the role is unknown */
    suspend fun known(): Boolean? {
        val backend = backends.selected() ?: return null
        return authRepository.role(backend.id)?.let(::allows)
    }

    /** asks the server for the role, @return what it allows, or what the last known role allows when the server fails */
    suspend fun refresh(): Boolean? {
        val backend = backends.selected()?.toBackendRef() ?: return null
        if (!authRepository.isSignedIn(backend.id)) return null
        return try {
            allows(authRepository.refreshRole(backend))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not load the role of the account")
            known()
        }
    }

    private fun allows(role: String) = role != AccountRole.READER
}
