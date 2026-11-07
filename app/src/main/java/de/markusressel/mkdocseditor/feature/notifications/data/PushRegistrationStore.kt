package de.markusressel.mkdocseditor.feature.notifications.data

import de.markusressel.mkdocseditor.auth.api.PushSubscription
import de.markusressel.mkdocseditor.auth.token.KeyValueStore

/**
 * What [de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesPushRegistration] registered: the VAPID
 * key, the endpoint and keys the distributor answered with, and where they were sent to
 */
internal class PushRegistrationStore(private val storage: KeyValueStore) {

    /** the key registered with, null while not registered */
    var vapidKey: String?
        get() = storage.get(VAPID_KEY)
        set(value) = set(VAPID_KEY, value)

    var subscription: PushSubscription?
        get() {
            val endpoint = storage.get(ENDPOINT) ?: return null
            val p256dh = storage.get(P256DH) ?: return null
            val auth = storage.get(AUTH) ?: return null
            return PushSubscription(endpoint, p256dh, auth)
        }
        set(value) {
            // the distributor answers each registration, mostly with the same endpoint
            if (value == subscription) return
            set(ENDPOINT, value?.endpoint)
            set(P256DH, value?.p256dh)
            set(AUTH, value?.auth)
            subscribedAs = null
        }

    var subscribedBackend: Long?
        get() = storage.get(SUBSCRIBED_BACKEND)?.toLongOrNull()
        set(value) = set(SUBSCRIBED_BACKEND, value?.toString())

    /** tells whether [subscription] was sent for the current session already */
    var subscribedAs: String?
        get() = storage.get(SUBSCRIBED_AS)
        set(value) = set(SUBSCRIBED_AS, value)

    /** the server has push, but there is no distributor app */
    var distributorMissing: Boolean
        get() = storage.get(DISTRIBUTOR_MISSING) == "true"
        set(value) = set(DISTRIBUTOR_MISSING, if (value) "true" else null)

    /** forgets the registration, but not [distributorMissing] */
    fun clear() {
        vapidKey = null
        subscription = null
        subscribedBackend = null
    }

    private fun set(key: String, value: String?) = if (value == null) storage.remove(key) else storage.put(key, value)

    private companion object {
        const val VAPID_KEY = "vapidKey"
        const val ENDPOINT = "endpoint"
        const val P256DH = "p256dh"
        const val AUTH = "auth"
        const val SUBSCRIBED_BACKEND = "subscribedBackend"
        const val SUBSCRIBED_AS = "subscribedAs"
        const val DISTRIBUTOR_MISSING = "distributorMissing"
    }
}
