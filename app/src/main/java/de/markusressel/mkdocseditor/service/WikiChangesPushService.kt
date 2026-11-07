package de.markusressel.mkdocseditor.service

import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.mkdocseditor.auth.api.PushSubscription
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesPushHandler
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesPushRegistration
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import timber.log.Timber
import javax.inject.Inject

/**
 * Gets what the UnifiedPush distributor sends. The connector decrypts the messages with the keys of the endpoint, which
 * are the ones sent to the backend.
 */
@AndroidEntryPoint
internal class WikiChangesPushService : PushService() {

    @Inject
    internal lateinit var registration: WikiChangesPushRegistration

    @Inject
    internal lateinit var handler: WikiChangesPushHandler

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val keys = endpoint.pubKeySet
        if (keys == null) {
            // without keys the backend can't encrypt
            Timber.w("The distributor sent an endpoint without keys")
            return
        }
        val subscription = PushSubscription(endpoint.url, p256dh = keys.pubKey, auth = keys.auth)
        MainScope().launch { registration.onNewEndpoint(subscription) }
    }

    override fun onMessage(message: PushMessage, instance: String) = handler.onMessage(message.content, message.decrypted)

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        // the hourly check goes on
        Timber.w("Push registration failed: $reason")
    }

    override fun onUnregistered(instance: String) {
        MainScope().launch { registration.onUnregistered() }
    }
}
