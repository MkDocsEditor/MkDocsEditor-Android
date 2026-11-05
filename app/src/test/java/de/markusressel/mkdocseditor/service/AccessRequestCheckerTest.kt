package de.markusressel.mkdocseditor.service

import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AccessRequestInfo
import de.markusressel.mkdocseditor.auth.api.AccessRequestState
import de.markusressel.mkdocseditor.auth.api.AccessRequestStatus
import de.markusressel.mkdocseditor.auth.api.AccountInfo
import de.markusressel.mkdocseditor.auth.api.ServerFeature
import de.markusressel.mkdocseditor.auth.api.ServerInfo
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.token.AccessRequestStore
import de.markusressel.mkdocseditor.auth.token.AuthSession
import de.markusressel.mkdocseditor.auth.token.KeyValueStore
import de.markusressel.mkdocseditor.auth.token.PendingAccessRequest
import de.markusressel.mkdocseditor.auth.token.TokenCipher
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.backendConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessRequestCheckerTest {

    private class MapStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private val answers = mutableListOf<Boolean>()
    private val announced = mutableListOf<Int>()
    private val api = object : FakeAuthApi() {
        var role = "admin"
        override suspend fun account(token: String) = AccountInfo(id = "a", username = "u", displayName = "U", role = role)
    }.apply { onServerInfo = { ServerInfo(features = listOf(ServerFeature.ACCESS_REQUESTS)) } }
    private val tokens = InMemoryTokenStore()
    private val backends = FakeAuthBackends()
    private val store = AccessRequestStore(MapStore(), object : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(data: ByteArray) = data
    })
    private val checker = AccessRequestChecker(
        requestStore = store,
        backends = backends,
        authRepository = AuthRepository(tokens, { api }, deviceName = "Pixel 8"),
        notifier = object : AccessRequestNotifier {
            override fun onRequestAnswered(request: PendingAccessRequest, approved: Boolean) { answers += approved }
            override fun onNewRequests(backendId: Long, backendName: String, count: Int) { announced += count }
        },
        seen = SeenAccessRequests(MapStore()),
    )
    private val request = PendingAccessRequest("https://b/", "mda_x", "https://c/join/1", "Wiki")

    private fun signedIn() {
        backends.configs += backendConfig(1)
        tokens.put(1, AuthSession("t", null, "admin"))
    }

    private fun pending(vararg ids: String) {
        api.pendingAccessRequests = ids.map { AccessRequestInfo(id = it, name = it, status = AccessRequestState.PENDING) }
    }

    @Test
    fun `tells about an approval once, and keeps the request for the invite`() = runTest {
        store.put(request)
        api.accessRequestStatus = AccessRequestStatus(AccessRequestState.APPROVED, inviteToken = "mdl_x")

        checker()
        checker()

        assertEquals(listOf(true), answers)
        assertEquals("mda_x", store.get()?.token)
    }

    @Test
    fun `a used invite ends the request without a notification`() = runTest {
        store.put(request)
        api.accessRequestStatus = AccessRequestStatus(AccessRequestState.APPROVED)

        checker()

        assertTrue(answers.isEmpty())
        assertNull(store.get())
    }

    @Test
    fun `tells about a rejection and forgets the request`() = runTest {
        store.put(request)
        api.accessRequestStatus = AccessRequestStatus(AccessRequestState.REJECTED)

        checker()

        assertEquals(listOf(false), answers)
        assertNull(store.get())
    }

    @Test
    fun `admins hear about each new request once`() = runTest {
        signedIn()
        pending("1")
        checker()
        checker()
        pending("1", "2")
        checker()

        assertEquals(listOf(1, 2), announced)
    }

    @Test
    fun `others do not ask for the list`() = runTest {
        signedIn()
        api.role = "editor"

        checker()

        assertTrue("pendingAccessRequests" !in api.calls)
        assertTrue(announced.isEmpty())
    }
}
