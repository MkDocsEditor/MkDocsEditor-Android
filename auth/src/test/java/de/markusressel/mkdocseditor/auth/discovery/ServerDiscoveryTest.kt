package de.markusressel.mkdocseditor.auth.discovery

import de.markusressel.mkdocseditor.auth.api.OkHttpAuthApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ServerDiscoveryTest {

    private val web = MockWebServer()
    private val backend = MockWebServer()
    private val client = OkHttpClient()
    private val discovery = ServerDiscovery(client) { OkHttpAuthApi(it, client) }
    private val requests = mutableListOf<String>()

    private val serverInfoJson =
        """{"name": "Markus' Wiki", "wikiUrl": "https://wiki.example.com", "webClientUrl": null, "passkeys": {"enabled": true, "rpId": "example.com"}, "loginMethods": ["password", "passkey", "device-link"]}"""

    private fun serve(server: MockWebServer, routes: Map<String, Pair<Int, String>>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                requests += "${server.port}$path"
                val (code, body) = routes[path] ?: (404 to """{"name":"Not Found","code":"not-found","message":"not found"}""")
                return MockResponse.Builder().code(code).body(body).build()
            }
        }
    }

    @Before
    fun setUp() {
        web.start()
        backend.start()
    }

    @After
    fun tearDown() {
        web.close()
        backend.close()
    }

    @Test
    fun `a web client address finds the backend through config json`() = runTest {
        val backendUrl = backend.url("/api/").toString()
        serve(web, mapOf("/config.json" to (200 to """{"backendUrl": "${backendUrl.trimEnd('/')}", "wikiUrl": "https://wiki.example.com"}""")))
        serve(backend, mapOf("/api/server-info/" to (200 to serverInfoJson)))

        val found = assertIs<DiscoveryResult.Found>(discovery.discover(web.url("/").toString())).server
        assertEquals(backendUrl, found.backendUrl)
        assertEquals("https://wiki.example.com", found.wikiUrl)
        assertEquals(web.url("/").toString(), found.webClientUrl)
        assertEquals("Markus' Wiki", found.displayName)
    }

    @Test
    fun `a backend address is used directly`() = runTest {
        serve(backend, mapOf("/server-info/" to (200 to serverInfoJson)))
        val found = assertIs<DiscoveryResult.Found>(discovery.discover(backend.url("/").toString())).server
        assertEquals(backend.url("/").toString(), found.backendUrl)
        assertEquals(listOf("${backend.port}/config.json", "${backend.port}/server-info/"), requests)
    }

    @Test
    fun `a deep link into the web client falls back to its root`() = runTest {
        serve(web, mapOf("/config.json" to (200 to """{"backendUrl": "${backend.url("/").toString().trimEnd('/')}"}""")))
        serve(backend, mapOf("/server-info/" to (200 to serverInfoJson)))
        val found = assertIs<DiscoveryResult.Found>(discovery.discover(web.url("/documents/abc").toString())).server
        assertEquals(backend.url("/").toString(), found.backendUrl)
        assertEquals("https://wiki.example.com", found.wikiUrl, "from server info when config.json has none")
    }

    @Test
    fun `an older backend without server info is found through auth status`() = runTest {
        serve(backend, mapOf("/auth/status/" to (200 to """{"setupRequired": true}""")))
        val found = assertIs<DiscoveryResult.Found>(discovery.discover(backend.url("/").toString())).server
        assertEquals(true, found.info.setupRequired)
        assertNull(found.info.name)
        assertEquals(backend.url("/").host, found.displayName)
    }

    @Test
    fun `a site that is no MkDocsEditor`() = runTest {
        serve(web, mapOf("/" to (200 to "<html></html>"), "/config.json" to (200 to "<html>spa fallback</html>")))
        assertEquals(DiscoveryResult.NotMkDocsEditor, discovery.discover(web.url("/").toString()))
    }

    @Test
    fun `nothing listening and invalid input`() = runTest {
        val port = web.port
        web.close()
        assertIs<DiscoveryResult.Unreachable>(discovery.discover("http://127.0.0.1:$port"))
        assertEquals(DiscoveryResult.InvalidAddress, discovery.discover("not an address"))
        assertEquals(DiscoveryResult.InvalidAddress, discovery.discover("ftp://example.com"))
    }

    @Test
    fun `address normalization`() {
        assertEquals("https://wiki.example.com/", ServerDiscovery.normalizeAddress("Wiki.Example.com"))
        assertEquals("http://192.168.2.20:7413/", ServerDiscovery.normalizeAddress(" http://192.168.2.20:7413 "))
        assertEquals("https://example.com/editor/", ServerDiscovery.normalizeAddress("https://example.com/editor/index.html?x=1#y"))
        assertEquals("https://b.example.com/api/", ServerDiscovery.resolve("https://e.example.com/", "https://b.example.com/api"))
        assertEquals("https://e.example.com/api/", ServerDiscovery.resolve("https://e.example.com/", "/api"))
    }
}
