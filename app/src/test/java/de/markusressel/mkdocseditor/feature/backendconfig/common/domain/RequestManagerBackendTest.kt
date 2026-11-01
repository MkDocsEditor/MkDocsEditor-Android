package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import android.app.Application
import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.BackendNotConfiguredException
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.RequestManager
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Android can restore any screen after process death, without the code that used to apply the
 * backend to the client. These tests start with a new client, like such a process does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class RequestManagerBackendTest {

    private lateinit var server: TestHttpServer

    private val serverUrl get() = "http://127.0.0.1:${server.port}/"

    @BeforeTest
    fun startServer() {
        server = TestHttpServer()
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    /**
     * The bug: the first requests after process death went to https://localhost:8080
     */
    @Test
    fun aClientWithoutBackendDoesNotConnectToTheDefaultHost() = runBlocking<Unit> {
        val defaultHost = try {
            ServerSocket(8080, 8, InetAddress.getByName("127.0.0.1"))
        } catch (ex: IOException) {
            assumeTrue("port 8080 is in use", false)
            throw ex
        }
        defaultHost.use { socket ->
            val connected = AtomicBoolean(false)
            thread(isDaemon = true) {
                try {
                    socket.accept().use { connected.set(true) }
                } catch (_: IOException) {
                    // closed at the end of the test
                }
            }

            val failure = MkDocsRestClient().isHostAlive().component2()

            assertNotNull(failure)
            assertFalse(connected.get(), "the client connected to localhost:8080")
        }
    }

    @Test
    fun aRequestWithoutBackendFailsAsNotConfigured() = runBlocking<Unit> {
        val failure = MkDocsRestClient().getItemTree().component2()

        assertIs<BackendNotConfiguredException>(assertNotNull(failure).exception)
    }

    @Test
    fun theFirstRequestOfANewClientGoesToTheBackendWithTheSession() = runBlocking<Unit> {
        val client = MkDocsRestClient(
            RequestManager(
                baseUrl = { serverUrl },
                authorizationProvider = AuthorizationProvider.bearer { "token" },
            )
        )

        client.isHostAlive()

        assertEquals(listOf<Pair<String, String?>>("/alive/" to "Bearer token"), server.received.toList())
    }

    @Test
    fun theBaseUrlKeepsItsPath() = runBlocking<Unit> {
        val client = MkDocsRestClient(RequestManager(baseUrl = { serverUrl + "wiki/" }))

        client.isHostAlive()

        assertEquals("/wiki/alive/", server.received.single().first)
    }

    @Test
    fun aChangedBackendCountsFromTheNextRequestOn() = runBlocking<Unit> {
        var baseUrl: String? = null
        val client = MkDocsRestClient(RequestManager(baseUrl = { baseUrl }))

        client.isHostAlive()
        assertEquals(0, server.received.size)

        baseUrl = serverUrl
        client.isHostAlive()
        assertEquals(1, server.received.size)

        baseUrl = null
        client.isHostAlive()
        assertEquals(1, server.received.size)
    }

    @Test
    fun anUnauthorizedAnswerCallsTheListener() = runBlocking<Unit> {
        var calls = 0
        server.status = 401
        val client = MkDocsRestClient(RequestManager(baseUrl = { serverUrl }, unauthorizedListener = { calls++ }))

        client.isHostAlive()

        assertEquals(1, calls)
    }
}
