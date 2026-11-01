package de.markusressel.mkdocseditor.feature.editor

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.HttpException
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.feature.editor.domain.PageReferencesLoader
import de.markusressel.mkdocseditor.feature.editor.domain.withFixedReference
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferences
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferencesError
import de.markusressel.mkdocsrestclient.reference.BacklinksModel
import de.markusressel.mkdocsrestclient.reference.DocumentReferencesModel
import de.markusressel.mkdocsrestclient.reference.ItemRefModel
import de.markusressel.mkdocsrestclient.reference.ReferenceModel
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.test.runTest
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageReferencesLoaderTest {

    private fun failure(status: Int): FuelError =
        FuelError.wrap(HttpException(status, "error"), Response(URL("http://backend.invalid"), statusCode = status))

    private val item = ItemRefModel(type = "document", id = "d", name = "page", path = "page.md")

    private suspend fun load(
        references: Result<DocumentReferencesModel, FuelError>,
        backlinks: Result<BacklinksModel, FuelError>,
    ) = PageReferencesLoader({ references }, { backlinks }).load("d").fold(PageReferences()) { state, change -> change(state) }

    @Test
    fun `both lists arrive`() = runTest {
        val state = load(
            Result.success(DocumentReferencesModel(item, emptyList())),
            Result.success(BacklinksModel(item, emptyList())),
        )
        assertEquals(PageReferences(references = emptyList(), backlinks = emptyList()), state)
    }

    @Test
    fun `an older backend and a missing connection are told apart, each list on its own`() = runTest {
        val state = load(Result.error(failure(404)), Result.error(failure(500)))
        assertEquals(PageReferencesError.UNSUPPORTED, state.referencesError)
        assertEquals(PageReferencesError.OFFLINE, state.backlinksError)
        assertNull(state.references)
    }

    @Test
    fun `fixing a reference only changes that reference`() {
        val broken = ReferenceModel(kind = "link", line = 1, column = 1, destination = "old.md", anchor = "", status = "moved", target = null, suggestedDestination = "new.md")
        val other = broken.copy(line = 2)
        val fixed = PageReferences(references = listOf(broken, other)).withFixedReference(broken, "new.md")
        assertEquals("new.md", fixed.references!![0].destination)
        assertEquals("ok", fixed.references!![0].status)
        assertNull(fixed.references!![0].suggestedDestination)
        assertEquals(other, fixed.references!![1])
    }
}
