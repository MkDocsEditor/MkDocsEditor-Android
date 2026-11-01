package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferences
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferencesDialog
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.doc
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.res
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocsrestclient.reference.BacklinkModel
import de.markusressel.mkdocsrestclient.reference.ItemRefModel
import de.markusressel.mkdocsrestclient.reference.ReferenceModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class PageReferencesDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * root: index.md, logo.png
     *   Rezepte: Apfelkuchen.md (the open page), Kuchen.jpg, Teig.png, Liste.pdf
     */
    private val rezepte = section(
        "s-r", "Rezepte",
        documents = listOf(doc("d-apfel", "Apfelkuchen")),
        resources = listOf(res("r-kuchen", "Kuchen.jpg"), res("r-teig", "Teig.png"), res("r-liste", "Liste.pdf")),
    )
    private val tree = WikiTree(
        section("root", "root", subsections = listOf(rezepte), documents = listOf(doc("d-index", "index")), resources = listOf(res("r-logo", "logo.png")))
    )

    private fun ref(type: String, id: String, path: String) = ItemRefModel(type, id, path, path.substringAfterLast('/'))

    private val references = listOf(
        ReferenceModel("image", 3, 3, "Kuchen.jpg", "", "ok", ref("resource", "r-kuchen", "Rezepte/Kuchen.jpg")),
        ReferenceModel("image", 4, 3, "../logo.png", "", "ok", ref("resource", "r-logo", "logo.png")),
        ReferenceModel("image", 5, 3, "weg.png", "", "broken", null, reason = "not-found", path = "Rezepte/weg.png"),
        ReferenceModel("link", 6, 8, "../index.md", "", "ok", ref("document", "d-index", "index.md")),
        ReferenceModel(
            "link", 7, 8, "../Alt/Suppe.md", "", "broken", null, reason = "not-found", path = "Alt/Suppe.md",
            movedTo = ref("document", "d-suppe", "Neu/Suppe.md"), suggestedDestination = "../Neu/Suppe.md",
        ),
    )

    private val inserted = mutableListOf<String>()
    private val jumps = mutableListOf<ReferenceModel>()
    private val fixes = mutableListOf<ReferenceModel>()
    private val opened = mutableListOf<String>()
    private var uploads = 0

    private fun panel(state: PageReferences) = composeRule.setContent {
        MkDocsEditorTheme {
            PageReferencesDialog(
                tree = tree,
                documentId = "d-apfel",
                state = state,
                thumbnailLoader = null,
                onInsert = { inserted += it },
                onJump = { jumps += it },
                onFix = { fixes += it },
                onOpenDocument = { opened += it },
                onUpload = { uploads++ },
                onDismiss = {},
            )
        }
    }

    @Test
    fun `files used by the page come first, then the rest of the folder`() {
        panel(PageReferences(references = references, backlinks = emptyList()))
        composeRule.onAllNodesWithTag("references-used-resource").assertCountEquals(2)
        composeRule.onAllNodesWithTag("references-folder-resource").assertCountEquals(2)
        // the broken image is listed with the used files
        composeRule.onAllNodesWithTag("references-broken").assertCountEquals(1)
        composeRule.onNodeWithText("Liste.pdf").performClick()
        composeRule.onNodeWithText("logo.png").performClick()
        assertEquals(listOf("[Liste.pdf](Liste.pdf)", "![logo](../logo.png)"), inserted)
    }

    @Test
    fun `find selects the first use of a file`() {
        panel(PageReferences(references = references, backlinks = emptyList()))
        composeRule.onAllNodesWithTag("references-find")[0].performClick()
        assertEquals(listOf(references[0]), jumps)
    }

    @Test
    fun `upload goes to the folder of the page`() {
        panel(PageReferences(references = references, backlinks = emptyList()))
        composeRule.onNodeWithText("Upload to Rezepte").performClick()
        assertEquals(1, uploads)
    }

    @Test
    fun `the links tab counts broken references and offers fixes for moved targets`() {
        panel(PageReferences(references = references, backlinks = emptyList()))
        composeRule.onNodeWithText("Links (2 broken)").performClick()
        composeRule.onAllNodesWithTag("references-ok").assertCountEquals(3)
        composeRule.onAllNodesWithTag("references-broken").assertCountEquals(2)
        composeRule.onNodeWithText("Line 7 · broken: moved to Neu/Suppe.md").assertExists()
        composeRule.onAllNodesWithTag("references-fix").assertCountEquals(1)
        composeRule.onNodeWithTag("references-fix").performClick()
        assertEquals(listOf(references[4]), fixes)
        composeRule.onNodeWithText("../index.md").performClick()
        assertEquals(listOf(references[3]), jumps)
    }

    @Test
    fun `used by opens the linking page`() {
        val target = ref("document", "d-apfel", "Rezepte/Apfelkuchen.md")
        panel(
            PageReferences(
                references = emptyList(),
                backlinks = listOf(BacklinkModel(ref("document", "d-index", "index.md"), "link", 2, 4, "Rezepte/Apfelkuchen.md", "", target)),
            )
        )
        composeRule.onNodeWithTag("tab-used-by").performClick()
        composeRule.onNodeWithText("index.md").performClick()
        assertEquals(listOf("d-index"), opened)
    }

    @Test
    fun `errors and empty lists`() {
        panel(PageReferences(referencesError = "Needs a connection to the server.", backlinks = emptyList()))
        composeRule.onNodeWithTag("references-error").assertExists()
        composeRule.onNodeWithTag("tab-links").performClick()
        composeRule.onNodeWithTag("references-error").assertExists()
        composeRule.onNodeWithTag("tab-used-by").performClick()
        composeRule.onNodeWithText("No page links here.").assertExists()
    }
}
