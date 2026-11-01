package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.feature.editor.domain.WikiTreeHolder
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WikiTreeHolderTest {

    private val withPage = sectionData("root", documents = listOf(documentData("page")))
    private val withoutPage = sectionData("root")
    private var freshLoads = 0

    private fun holder(cached: SectionData?, fresh: SectionData?) = WikiTreeHolder(
        cached = { cached ?: throw IOException("no cache") },
        fresh = {
            freshLoads++
            fresh ?: throw IOException("offline")
        },
    )

    @Test
    fun `a cached tree with the document is enough`() = runTest {
        val holder = holder(cached = withPage, fresh = null)
        assertNotNull(holder.load("page"))
        assertEquals(0, freshLoads)
        assertNotNull(holder.tree.value)
    }

    @Test
    fun `a cached tree without the document is too old, the server is asked`() = runTest {
        val holder = holder(cached = withoutPage, fresh = withPage)
        assertNotNull(holder.load("page")?.documentById("page"))
        assertEquals(1, freshLoads)
    }

    @Test
    fun `without a cache and a server there is no tree`() = runTest {
        val holder = holder(cached = null, fresh = null)
        assertNull(holder.load("page"))
        assertNull(holder.tree.value)
    }

    @Test
    fun `a failed refresh keeps the tree`() = runTest {
        val holder = holder(cached = withPage, fresh = null)
        holder.load("page")
        assertNull(holder.refresh())
        assertNotNull(holder.tree.value)
    }
}
