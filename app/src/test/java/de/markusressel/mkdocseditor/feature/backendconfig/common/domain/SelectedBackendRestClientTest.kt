package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import android.app.Application
import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AuthApiFactory
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.token.AuthSession
import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocsrestclient.BackendNotConfiguredException
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a new process gets when Android restores a screen after process death: only the
 * database and the session store are there, no screen has run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SelectedBackendRestClientTest {

    private lateinit var server: TestHttpServer

    private val tokenStore = InMemoryTokenStore()
    private val authRepository = AuthRepository(
        tokenStore = tokenStore,
        apiFactory = AuthApiFactory { FakeAuthApi() },
        deviceName = "test",
    )

    @BeforeTest
    fun startServer() {
        server = TestHttpServer()
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    private fun localBackend(id: Long): BackendConfig = backendConfig(id).copy(
        serverConfig = BackendServerConfig(domain = "127.0.0.1", port = server.port, useSsl = false),
    )

    private fun signIn(backendId: Long) {
        tokenStore.put(backendId, AuthSession(token = "session-$backendId", expiresAt = null, username = "markus"))
    }

    @Test
    fun theFirstRequestAfterProcessDeathGoesToTheSelectedBackendWithItsSession() = runBlocking<Unit> {
        signIn(7)
        val client = createSelectedBackendRestClient({ localBackend(7) }, authRepository)

        assertNotNull(client.isHostAlive().component1())

        assertEquals(listOf<String?>("Bearer session-7"), server.received.map { it.second })
    }

    @Test
    fun withoutSelectedBackendNoRequestIsMade() = runBlocking<Unit> {
        val client = createSelectedBackendRestClient({ null }, authRepository)

        val failure = client.getItemTree().component2()

        assertIs<BackendNotConfiguredException>(assertNotNull(failure).exception)
        assertEquals(0, server.received.size)
    }

    @Test
    fun aDatabaseErrorCountsAsNotConfigured() = runBlocking<Unit> {
        val client = createSelectedBackendRestClient({ error("database closed") }, authRepository)

        val failure = client.getItemTree().component2()

        assertIs<BackendNotConfiguredException>(assertNotNull(failure).exception)
    }

    @Test
    fun selectingAnotherBackendAppliesToTheNextRequest() = runBlocking<Unit> {
        signIn(1)
        signIn(2)
        var selected = localBackend(1)
        val client = createSelectedBackendRestClient({ selected }, authRepository)

        client.isHostAlive()
        selected = localBackend(2)
        client.isHostAlive()

        assertEquals(listOf<String?>("Bearer session-1", "Bearer session-2"), server.received.map { it.second })
    }

    @Test
    fun aRejectedSessionEndsTheSessionOfTheSelectedBackendOnly() = runBlocking<Unit> {
        signIn(1)
        signIn(2)
        server.status = 401
        val client = createSelectedBackendRestClient({ localBackend(2) }, authRepository)

        client.isHostAlive()

        assertTrue(authRepository.isSignedIn(1))
        assertFalse(authRepository.isSignedIn(2))
    }
}
