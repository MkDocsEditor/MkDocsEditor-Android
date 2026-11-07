package de.markusressel.mkdocseditor.feature.notifications.data

import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.PushSubscription
import de.markusressel.mkdocseditor.auth.api.ServerFeature
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import de.markusressel.mkdocseditor.feature.notifications.domain.PushServer
import de.markusressel.mkdocseditor.feature.notifications.domain.PushTarget
import java.security.MessageDigest
import javax.inject.Inject

/** The push endpoints need a session, app tokens can't subscribe */
internal class AuthPushServer @Inject constructor(
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
) : PushServer {

    override suspend fun target(): PushTarget? {
        val backend = backends.selected()?.toBackendRef() ?: return null
        val session = authRepository.session(backend.id) ?: return null
        val info = try {
            authRepository.serverInfo(backend)
        } catch (e: AuthApiException) {
            // a backend from before server info
            if (e.status == 404) return null else throw e
        }
        if (ServerFeature.PUSH !in info.features) return null
        val vapidKey = info.push?.vapidPublicKey ?: return null
        return PushTarget(backend.id, vapidKey, session = fingerprint(session.token))
    }

    override suspend fun subscribe(target: PushTarget, subscription: PushSubscription) {
        val backend = backends.get(target.backendId)?.toBackendRef() ?: return
        authRepository.authenticated(backend) { subscribePush(it, subscription) }
    }

    override suspend fun unsubscribe(backendId: Long, endpoint: String) {
        val backend = backends.get(backendId)?.toBackendRef() ?: return
        if (!authRepository.isSignedIn(backendId)) return
        authRepository.authenticated(backend) { unsubscribePush(it, endpoint) }
    }

    /** a hash, so the store never holds the token */
    private fun fingerprint(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}
