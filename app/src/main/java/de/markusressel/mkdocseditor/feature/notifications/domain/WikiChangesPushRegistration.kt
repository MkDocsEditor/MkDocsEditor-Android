package de.markusressel.mkdocseditor.feature.notifications.domain

import de.markusressel.mkdocseditor.auth.api.PushSubscription
import de.markusressel.mkdocseditor.feature.notifications.data.PushRegistrationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where push messages come from
 *
 * @param session tells sessions apart, because a subscription belongs to the session that made it; no token
 */
internal data class PushTarget(
    val backendId: Long,
    val vapidKey: String,
    val session: String,
)

/** The selected server as push needs it */
internal interface PushServer {
    /**
     * @return null without a session on the selected server, or when it has push off
     * @throws Exception when the server can't be asked
     */
    suspend fun target(): PushTarget?

    suspend fun subscribe(target: PushTarget, subscription: PushSubscription)

    /** with the session of [backendId], nothing without one */
    suspend fun unsubscribe(backendId: Long, endpoint: String)
}

/** The UnifiedPush distributor, an app like ntfy */
internal interface PushDistributor {
    /** @return false if there is no distributor, picks one if none is picked yet */
    fun select(): Boolean

    /** the distributor answers with a new endpoint, see [WikiChangesPushRegistration.onNewEndpoint] */
    fun register(vapidKey: String)

    fun unregister()
}

/**
 * Keeps the UnifiedPush registration and the subscription on the selected server in line with the session and the
 * setting, see docs/push.md of MkDocsEditor-Backend. Without a distributor, or with push off on the server, nothing
 * is registered, and [CheckWikiChangesUseCase] still runs about once an hour.
 */
@Singleton
internal class WikiChangesPushRegistration @Inject constructor(
    private val server: PushServer,
    private val distributor: PushDistributor,
    private val store: PushRegistrationStore,
) {
    private val mutex = Mutex()

    /** Registers or unregisters as the selected server, its session and [enabled] say */
    suspend fun sync(enabled: Boolean) = mutex.withLock {
        val target = try {
            if (enabled) server.target() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline, for example: keep what is registered
            Timber.w(e, "Could not ask the server about push")
            return@withLock
        }
        if (target == null) {
            store.distributorMissing = false
            stop()
            return@withLock
        }
        if (!distributor.select()) {
            store.distributorMissing = true
            stop()
            return@withLock
        }
        store.distributorMissing = false
        if (store.vapidKey != target.vapidKey) {
            // an endpoint is bound to the key it was registered with
            store.subscription = null
            store.vapidKey = target.vapidKey
        }
        distributor.register(target.vapidKey)
        store.subscription?.let { subscribe(target, it) }
    }

    suspend fun onNewEndpoint(subscription: PushSubscription) = mutex.withLock {
        if (store.vapidKey == null) return@withLock
        store.subscription = subscription
        val target = try {
            server.target()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not ask the server about push")
            null
        } ?: return@withLock
        subscribe(target, subscription)
    }

    /** The distributor dropped the registration, for example because it was uninstalled */
    suspend fun onUnregistered() = mutex.withLock {
        store.clear()
    }

    /** Runs while the session still exists, so the server forgets the subscription at once */
    suspend fun beforeSignOut(backendId: Long) = mutex.withLock {
        if (store.subscribedBackend == backendId) stop()
    }

    private suspend fun subscribe(target: PushTarget, subscription: PushSubscription) {
        val subscribedAs = subscribedAs(target, subscription)
        if (store.subscribedAs == subscribedAs) return
        try {
            server.subscribe(target, subscription)
            store.subscribedBackend = target.backendId
            store.subscribedAs = subscribedAs
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not subscribe to push")
        }
    }

    private suspend fun stop() {
        val backendId = store.subscribedBackend
        val subscription = store.subscription
        if (backendId != null && subscription != null) {
            try {
                server.unsubscribe(backendId, subscription.endpoint)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the server drops it on its own once the session ends or the endpoint is gone
                Timber.w(e, "Could not unsubscribe from push")
            }
        }
        if (store.vapidKey != null) distributor.unregister()
        store.clear()
    }

    /** the same endpoint and keys need no second POST for the same session */
    private fun subscribedAs(target: PushTarget, subscription: PushSubscription) =
        listOf(target.backendId, target.session, subscription.endpoint, subscription.p256dh, subscription.auth)
            .joinToString("\n")
            .hashCode()
            .toString()
}
