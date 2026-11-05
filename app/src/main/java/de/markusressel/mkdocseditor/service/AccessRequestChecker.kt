package de.markusressel.mkdocseditor.service

import de.markusressel.mkdocseditor.auth.api.AccessRequestState
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.ServerFeature
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.token.AccessRequestStore
import de.markusressel.mkdocseditor.auth.token.KeyValueStore
import de.markusressel.mkdocseditor.auth.token.PendingAccessRequest
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import java.io.IOException
import javax.inject.Inject

/** What [AccessRequestChecker] tells the user */
internal interface AccessRequestNotifier {
    fun onRequestAnswered(request: PendingAccessRequest, approved: Boolean)

    fun onNewRequests(backendId: Long, backendName: String, count: Int)
}

/** Remembers which requests an admin was already told about */
internal class SeenAccessRequests(private val storage: KeyValueStore) {
    fun get(backendId: Long): Set<String> = storage.get("$backendId")?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    fun set(backendId: Long, ids: Set<String>) = storage.put("$backendId", ids.joinToString(","))
}

/**
 * Asks our own backends for news in the background, there is no push service. The person who asked learns about the
 * answer, the admins learn about new requests.
 */
internal class AccessRequestChecker @Inject constructor(
    private val requestStore: AccessRequestStore,
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
    private val notifier: AccessRequestNotifier,
    private val seen: SeenAccessRequests,
) {
    /** @return false if a server could not be asked, so this should run again soon */
    suspend operator fun invoke(): Boolean {
        val own = checkOwnRequest()
        return backends.all().map { config -> config.toBackendRef()?.let { checkAsAdmin(it, config.name) } ?: true }.all { it } && own
    }

    private suspend fun checkOwnRequest(): Boolean {
        val request = requestStore.get() ?: return true
        return try {
            val answer = authRepository.api(BackendRef(id = 0, baseUrl = request.backendUrl)).accessRequestStatus(request.token)
            when (answer.status) {
                AccessRequestState.APPROVED -> when {
                    // the invite was used, so there is nothing left to wait for
                    answer.inviteToken == null -> requestStore.clear()

                    // stays stored: the join screen needs it to get the invite
                    !request.notified -> {
                        requestStore.put(request.copy(notified = true))
                        notifier.onRequestAnswered(request, approved = true)
                    }
                }

                AccessRequestState.REJECTED -> {
                    requestStore.clear()
                    notifier.onRequestAnswered(request, approved = false)
                }

                AccessRequestState.EXPIRED -> requestStore.clear()
            }
            true
        } catch (e: AuthApiException) {
            if (e.code == AuthErrorCode.INVALID_ACCESS_REQUEST) requestStore.clear()
            true
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun checkAsAdmin(backend: BackendRef, name: String): Boolean {
        if (!authRepository.isSignedIn(backend.id)) return true
        return try {
            if (ServerFeature.ACCESS_REQUESTS !in authRepository.serverInfo(backend).features) return true
            val role = authRepository.authenticated(backend) { account(it).role }
            if (role != ROLE_ADMIN) return true
            val pending = authRepository.authenticated(backend) { pendingAccessRequests(it) }.map { it.id }.toSet()
            val known = seen.get(backend.id)
            if ((pending - known).isNotEmpty()) notifier.onNewRequests(backend.id, name, pending.size)
            seen.set(backend.id, pending)
            true
        } catch (_: AuthApiException) {
            // signed out or refused: nothing to tell, and trying again soon would not change that
            true
        } catch (_: IOException) {
            false
        }
    }

    private companion object {
        const val ROLE_ADMIN = "admin"
    }
}
