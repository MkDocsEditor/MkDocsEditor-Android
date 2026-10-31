package de.markusressel.mkdocseditor.feature.auth.onboarding

import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocseditor.feature.auth.common.data.BackendConfigPlanner
import de.markusressel.mkdocseditor.feature.auth.common.data.ServerSetup
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.MkDocsWebConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.baseUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackendConfigPlannerTest {

    @Test
    fun `a new server gets its instance name, port, SSL and wiki from discovery`() {
        val config = BackendConfigPlanner.plan(
            emptyList(),
            ServerSetup(backendUrl = "https://backend.wiki.example.com/", suggestedName = "Markus' Wiki", wikiUrl = "https://wiki.example.com"),
        )!!
        assertEquals(0L, config.id)
        assertEquals("Markus' Wiki", config.name)
        assertEquals("https://backend.wiki.example.com:443/", config.serverConfig!!.baseUrl)
        assertEquals(MkDocsWebConfig(domain = "wiki.example.com", port = 443, useSsl = true), config.mkDocsWebConfig)
    }

    @Test
    fun `plain http, ports and paths, and names that are taken`() {
        val existing = listOf(backendConfig(1, name = "192.168.2.20", domain = "other.example.com"))
        val config = BackendConfigPlanner.plan(existing, ServerSetup(backendUrl = "http://192.168.2.20:7413/api"))!!
        assertEquals("http://192.168.2.20:7413/api/", config.serverConfig!!.baseUrl)
        assertEquals("192.168.2.20 (2)", config.name)
    }

    @Test
    fun `the same backend URL reuses the existing config and keeps what the user set`() {
        val existing = listOf(
            backendConfig(7, name = "My wiki", domain = "backend.wiki.example.com"),
            backendConfig(8, name = "Other", domain = "x.example.com"),
        )
        val config = BackendConfigPlanner.plan(
            existing,
            ServerSetup(backendUrl = "https://backend.wiki.example.com", suggestedName = "Server name", wikiUrl = "https://wiki.example.com"),
        )!!
        assertEquals(7L, config.id)
        assertEquals("My wiki", config.name)
        assertEquals("wiki.example.com", config.mkDocsWebConfig?.domain, "fills in a missing wiki")
    }

    @Test
    fun `a name and wiki login typed in Advanced win`() {
        val auth = AuthConfig(username = "reader", password = "secret")
        val existing = listOf(backendConfig(7, name = "Old", domain = "backend.wiki.example.com"))
        val config = BackendConfigPlanner.plan(
            existing,
            ServerSetup(backendUrl = "https://backend.wiki.example.com/", name = "New", description = "Work", wikiAuth = auth),
        )!!
        assertEquals("New", config.name)
        assertEquals("Work", config.description)
        assertEquals(auth, config.mkDocsWebAuthConfig)
    }

    @Test
    fun `unusable URLs`() {
        assertNull(BackendConfigPlanner.plan(emptyList(), ServerSetup(backendUrl = "ftp://example.com")))
    }
}
