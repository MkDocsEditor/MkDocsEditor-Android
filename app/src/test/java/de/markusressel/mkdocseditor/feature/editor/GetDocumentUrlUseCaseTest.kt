package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.MkDocsWebConfig
import de.markusressel.mkdocseditor.feature.editor.domain.GetDocumentUrlUseCase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GetDocumentUrlUseCaseTest {

    private fun urlOf(domain: String, page: String = "kitchen/oven/", port: Int = 443): String? = runBlocking {
        val config = BackendConfig(
            name = "wiki",
            description = "",
            serverConfig = null,
            mkDocsWebConfig = MkDocsWebConfig(domain = domain, port = port, useSsl = true),
            isSelected = true,
        )
        GetDocumentUrlUseCase()(config, DocumentEntity(url = page))
    }

    @Test
    fun `a wiki url with the path of the site links below that path`() {
        assertEquals("https://wiki.example.com/view/kitchen/oven/", urlOf("wiki.example.com/view"))
        assertEquals("https://wiki.example.com/view/kitchen/oven/", urlOf("wiki.example.com/view/"))
    }

    @Test
    fun `a wiki url of only the host links below view as before`() {
        assertEquals("https://wiki.example.com/view/kitchen/oven/", urlOf("wiki.example.com"))
    }

    @Test
    fun `the index page links to the site itself`() {
        assertEquals("https://wiki.example.com/view/", urlOf("wiki.example.com/view", page = "index/"))
    }

    @Test
    fun `the link carries no login, the wiki accepts the session of the web client`() {
        assertEquals("https://wiki.example.com:8443/view/kitchen/oven/", urlOf("wiki.example.com/view", port = 8443))
    }
}
