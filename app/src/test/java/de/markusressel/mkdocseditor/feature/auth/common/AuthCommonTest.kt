package de.markusressel.mkdocseditor.feature.auth.common

import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.feature.auth.common.data.ParsedBackendUrl
import de.markusressel.mkdocseditor.feature.auth.common.data.toAuthMessage
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.baseUrl
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.eventsWebsocketUrl
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.websocketBaseUrl
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthCommonTest {

    @Test
    fun `parses backend urls`() {
        assertEquals(ParsedBackendUrl("backend.example.com", 443, true), ParsedBackendUrl.parse("backend.example.com"))
        assertEquals(ParsedBackendUrl("192.168.2.20", 7413, false), ParsedBackendUrl.parse(" http://192.168.2.20:7413/ "))
        assertEquals(ParsedBackendUrl("example.com/api", 8443, true), ParsedBackendUrl.parse("https://example.com:8443/api/"))
        assertNull(ParsedBackendUrl.parse("ftp://example.com"))
        assertNull(ParsedBackendUrl.parse(""))
    }

    @Test
    fun `builds the base url like the rest client`() {
        assertEquals("https://backend.example.com:443/", BackendServerConfig(domain = "backend.example.com", port = 443, useSsl = true).baseUrl)
        assertEquals("http://example.com:80/api/", BackendServerConfig(domain = "http://example.com/api/", port = 80, useSsl = false).baseUrl)
    }

    @Test
    fun `the document websocket is below the path of the backend`() {
        val config = BackendServerConfig(domain = "192.168.2.20/api", port = 7413, useSsl = false)
        assertEquals(
            "ws://192.168.2.20:7413/api/document/some-id/ws/",
            AutomergeDocumentSyncManager.documentWebsocketUrl(config.websocketBaseUrl, "some-id"),
        )
    }

    @Test
    fun `builds the events websocket url below the base url`() {
        assertEquals("wss://backend.example.com:443/events/ws/", BackendServerConfig(domain = "backend.example.com", port = 443, useSsl = true).eventsWebsocketUrl)
        assertEquals("ws://192.168.2.20:7413/api/events/ws/", BackendServerConfig(domain = "192.168.2.20/api", port = 7413, useSsl = false).eventsWebsocketUrl)
        assertEquals("wss://example.com:8443/events/ws/", BackendServerConfig(domain = "https://example.com", port = 8443, useSsl = true).eventsWebsocketUrl)
    }

    @Test
    fun `maps error codes to messages`() {
        assertEquals(R.string.auth_error_rate_limited, AuthApiException(429, AuthErrorCode.RATE_LIMITED, "", retryAfterSeconds = 30).toAuthMessage().resId)
        assertEquals(listOf<Any>(30L), AuthApiException(429, AuthErrorCode.RATE_LIMITED, "", retryAfterSeconds = 30).toAuthMessage().args)
        assertEquals(
            R.string.auth_error_password_too_short,
            AuthApiException(400, AuthErrorCode.WEAK_PASSWORD, "", details = mapOf("reason" to "too-short", "minLength" to 10.0)).toAuthMessage().resId
        )
        assertEquals(R.string.auth_error_password_common, AuthApiException(400, AuthErrorCode.WEAK_PASSWORD, "", details = mapOf("reason" to "common")).toAuthMessage().resId)
        assertEquals(R.string.auth_error_not_supported, AuthApiException(404, null, "Not Found").toAuthMessage().resId)
        assertEquals(R.string.auth_error_http, AuthApiException(500, "internal", "boom").toAuthMessage().resId)
        assertEquals(R.string.auth_error_network, IOException("timeout").toAuthMessage().resId)
    }

    @Test
    fun `the text of an exception never reaches the user`() {
        assertEquals(emptyList(), IOException("timeout").toAuthMessage().args)
        assertEquals(emptyList(), IllegalStateException("internal detail").toAuthMessage().args)
    }
}
