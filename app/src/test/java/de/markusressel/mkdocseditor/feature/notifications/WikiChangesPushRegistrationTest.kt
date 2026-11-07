package de.markusressel.mkdocseditor.feature.notifications

import de.markusressel.mkdocseditor.auth.api.PushSubscription
import de.markusressel.mkdocseditor.auth.token.MemoryKeyValueStore
import de.markusressel.mkdocseditor.feature.notifications.data.PushRegistrationStore
import de.markusressel.mkdocseditor.feature.notifications.domain.PushDistributor
import de.markusressel.mkdocseditor.feature.notifications.domain.PushServer
import de.markusressel.mkdocseditor.feature.notifications.domain.PushTarget
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesPushRegistration
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiChangesPushRegistrationTest {

    /** both fakes write into one list, so the tests see the order of the calls */
    private val calls = mutableListOf<String>()

    private val server = object : PushServer {
        var target: PushTarget? = PushTarget(backendId = 1, vapidKey = KEY, session = "s1")
        var offline = false

        override suspend fun target(): PushTarget? {
            if (offline) throw IOException("offline")
            return target
        }

        override suspend fun subscribe(target: PushTarget, subscription: PushSubscription) {
            calls += "subscribe:${target.backendId}:${target.session}:${subscription.endpoint}:${subscription.p256dh}"
        }

        override suspend fun unsubscribe(backendId: Long, endpoint: String) {
            calls += "unsubscribe:$backendId:$endpoint"
        }
    }

    private val distributor = object : PushDistributor {
        var installed = true

        override fun select() = installed

        override fun register(vapidKey: String) {
            calls += "register:$vapidKey"
        }

        override fun unregister() {
            calls += "unregister"
        }
    }

    private val store = PushRegistrationStore(MemoryKeyValueStore())
    private val registration = WikiChangesPushRegistration(server, distributor, store)

    /** what the distributor answers to a registration */
    private suspend fun registered(endpoint: String = ENDPOINT, p256dh: String = "BCVx") {
        registration.sync(enabled = true)
        registration.onNewEndpoint(PushSubscription(endpoint, p256dh = p256dh, auth = "BTBZ"))
    }

    @Test
    fun `registers with the key of the server and sends the endpoint and keys the distributor answers with`() = runTest {
        registered()

        assertEquals(listOf("register:$KEY", "subscribe:1:s1:$ENDPOINT:BCVx"), calls)
        assertFalse(store.distributorMissing)
    }

    @Test
    fun `the same endpoint is not sent twice for one session, but again for a new one`() = runTest {
        registered()
        calls.clear()

        registered()
        assertEquals(listOf("register:$KEY"), calls)

        calls.clear()
        server.target = server.target?.copy(session = "s2")
        registration.sync(enabled = true)
        assertEquals(listOf("register:$KEY", "subscribe:1:s2:$ENDPOINT:BCVx"), calls)
    }

    @Test
    fun `a new endpoint is sent again`() = runTest {
        registered()
        calls.clear()

        registration.onNewEndpoint(PushSubscription("https://ntfy.example.com/upNew", p256dh = "BNew", auth = "BTBZ"))

        assertEquals(listOf("subscribe:1:s1:https://ntfy.example.com/upNew:BNew"), calls)
    }

    @Test
    fun `without a distributor nothing is registered and the settings can say so`() = runTest {
        distributor.installed = false

        registration.sync(enabled = true)

        assertEquals(emptyList(), calls)
        assertTrue(store.distributorMissing)
    }

    @Test
    fun `when the server turns push off, the subscription is deleted before unregistering`() = runTest {
        registered()
        calls.clear()

        server.target = null
        registration.sync(enabled = true)

        assertEquals(listOf("unsubscribe:1:$ENDPOINT", "unregister"), calls)
        assertNull(store.vapidKey)
        assertNull(store.subscription)
        assertFalse(store.distributorMissing)
    }

    @Test
    fun `turning the setting off unregisters`() = runTest {
        registered()
        calls.clear()

        registration.sync(enabled = false)

        assertEquals(listOf("unsubscribe:1:$ENDPOINT", "unregister"), calls)
    }

    @Test
    fun `signing out deletes the subscription, then unregisters`() = runTest {
        registered()
        calls.clear()

        registration.beforeSignOut(backendId = 2)
        assertEquals(emptyList(), calls, "another server had no subscription")

        registration.beforeSignOut(backendId = 1)
        assertEquals(listOf("unsubscribe:1:$ENDPOINT", "unregister"), calls)
    }

    @Test
    fun `after signing in again the endpoint is sent with the new session`() = runTest {
        registered()
        registration.beforeSignOut(backendId = 1)
        calls.clear()

        server.target = server.target?.copy(session = "s2")
        registered()

        assertEquals(listOf("register:$KEY", "subscribe:1:s2:$ENDPOINT:BCVx"), calls)
    }

    @Test
    fun `a server that can't be asked keeps the registration`() = runTest {
        registered()
        calls.clear()

        server.offline = true
        registration.sync(enabled = true)

        assertEquals(emptyList(), calls)
        assertEquals(KEY, store.vapidKey)
        assertEquals(ENDPOINT, store.subscription?.endpoint)
    }

    @Test
    fun `a new key of the server drops the endpoint of the old one`() = runTest {
        registered()
        calls.clear()

        server.target = server.target?.copy(vapidKey = "BNewKey")
        registration.sync(enabled = true)

        assertEquals(listOf("register:BNewKey"), calls)
        assertNull(store.subscription)
    }

    @Test
    fun `an endpoint after unregistering is ignored`() = runTest {
        registered()
        registration.onUnregistered()
        calls.clear()

        registration.onNewEndpoint(PushSubscription(ENDPOINT, p256dh = "BCVx", auth = "BTBZ"))

        assertEquals(emptyList(), calls)
    }

    private companion object {
        const val KEY = "BP4z"
        const val ENDPOINT = "https://ntfy.example.com/upAbC"
    }
}
