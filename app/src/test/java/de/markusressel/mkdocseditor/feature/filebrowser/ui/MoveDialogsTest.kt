package de.markusressel.mkdocseditor.feature.filebrowser.ui

import android.app.Application
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.doc
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveOutcome
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.BacklinksDialog
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.ItemActionsDialog
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.MoveDialog
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocsrestclient.reference.BacklinkModel
import de.markusressel.mkdocsrestclient.reference.ItemRefModel
import de.markusressel.mkdocsrestclient.reference.LineEditModel
import de.markusressel.mkdocsrestclient.reference.MoveResultModel
import de.markusressel.mkdocsrestclient.reference.MovedDocumentModel
import de.markusressel.mkdocsrestclient.reference.MovedItemModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class MoveDialogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * root: index.md
     *   guide: setup.md, deep/ (inner/)
     *   notes
     */
    private val inner = section("s-inner", "inner")
    private val deep = section("s-deep", "deep", subsections = listOf(inner))
    private val guide = section("s-guide", "guide", subsections = listOf(deep), documents = listOf(doc("d-setup", "setup")))
    private val notes = section("s-notes", "notes")
    private val tree = WikiTree(section("root", "root", subsections = listOf(guide, notes), documents = listOf(doc("d-index", "index"))))
    private val setup = WikiItem.Document(guide.documents.first())

    private val previews = mutableListOf<Pair<String?, String?>>()
    private var applied = 0
    private var back = 0

    private fun moveDialog(state: DialogState.Move) = composeRule.setContent {
        MkDocsEditorTheme {
            MoveDialog(
                state = state,
                onPreview = { parent, name -> previews += parent to name },
                onBack = { back++ },
                onApply = { applied++ },
                onDismissRequest = {},
            )
        }
    }

    @Test
    fun `the actions dialog offers rename move used by and delete`() {
        val actions = mutableListOf<ItemAction>()
        composeRule.setContent {
            MkDocsEditorTheme { ItemActionsDialog(item = setup, onAction = { actions += it }, onDismissRequest = {}) }
        }
        composeRule.onNodeWithText("setup.md").assertExists()
        listOf("action-rename", "action-move", "action-used-by", "action-delete").forEach {
            composeRule.onNodeWithTag(it).performClick()
        }
        assertEquals(listOf(ItemAction.Rename, ItemAction.Move, ItemAction.UsedBy, ItemAction.Delete), actions)
    }

    @Test
    fun `rename asks for a preview with the new name only`() {
        moveDialog(DialogState.Move(item = setup, renameOnly = true))
        composeRule.onNodeWithTag("move-preview").assertIsNotEnabled()
        composeRule.onNodeWithTag("move-name").performTextClearance()
        composeRule.onNodeWithTag("move-name").performTextInput(" install ")
        composeRule.onNodeWithTag("move-preview").assertIsEnabled().performClick()
        assertEquals(listOf<Pair<String?, String?>>(null to "install"), previews)
    }

    @Test
    fun `the current folder cannot be picked unless the name changes`() {
        moveDialog(DialogState.Move(item = setup, renameOnly = false, tree = tree))
        // the browser opens in the folder of the document
        composeRule.onNodeWithTag("browser-current").assertExists().assert(hasText("guide"))
        composeRule.onNodeWithTag("browser-pick-current").assertDoesNotExist()

        composeRule.onNodeWithTag("move-name").performTextClearance()
        composeRule.onNodeWithTag("move-name").performTextInput("install")
        composeRule.onNodeWithTag("browser-pick-current").performClick()
        assertEquals(listOf<Pair<String?, String?>>("s-guide" to "install"), previews)
    }

    @Test
    fun `the current folder can still be opened to reach the folders below it`() {
        moveDialog(DialogState.Move(item = setup, renameOnly = false, tree = tree))
        composeRule.onNodeWithTag("browser-up").performClick()
        // guide is the current folder: it opens, but its pick button is off
        composeRule.onAllNodesWithTag("browser-pick-section")[0].assertIsNotEnabled()
        composeRule.onNodeWithText("notes").assertExists()
        composeRule.onAllNodesWithTag("browser-pick-section")[1].assertIsEnabled().performClick()
        assertEquals(listOf<Pair<String?, String?>>("s-notes" to null), previews)
        composeRule.onNodeWithText("guide").performClick()
        composeRule.onNodeWithText("deep").assertExists()
    }

    @Test
    fun `a section cannot move into itself or below itself`() {
        moveDialog(DialogState.Move(item = WikiItem.Section(deep), renameOnly = false, tree = tree))
        composeRule.onNodeWithTag("browser-current").assert(hasText("guide"))
        // deep is listed but disabled; guide (its parent) cannot be picked without a new name
        composeRule.onNodeWithTag("browser-pick-section").assertIsNotEnabled()
        composeRule.onNodeWithTag("browser-pick-current").assertDoesNotExist()
        composeRule.onNodeWithTag("browser-section").performClick()
        composeRule.onNodeWithTag("browser-current").assert(hasText("guide"))
    }

    @Test
    fun `backend errors are explained`() {
        moveDialog(DialogState.Move(item = setup, renameOnly = true, error = MoveOutcome.Failed(409, "exists", "guide/install.md exists")))
        composeRule.onNodeWithTag("move-error").assert(hasText("An item with this name already exists there."))
    }

    @Test
    fun `the preview lists every changed line and applies`() {
        val result = MoveResultModel(
            dryRun = true,
            type = "document",
            oldId = "d-setup",
            newId = "d-new",
            oldPath = "guide/setup.md",
            newPath = "notes/setup.md",
            moved = listOf(MovedItemModel("document", "d-setup", "d-new", "guide/setup.md", "notes/setup.md")),
            documents = listOf(
                MovedDocumentModel("d-index", "d-index", "index.md", "index.md", listOf(LineEditModel(3, "[Setup](guide/setup.md)", "[Setup](notes/setup.md)"))),
                MovedDocumentModel("d-setup", "d-new", "guide/setup.md", "notes/setup.md", listOf(LineEditModel(1, "![](../logo.png)", "![](../logo.png)"))),
            ),
            config = emptyList(),
        )
        moveDialog(DialogState.Move(item = setup, renameOnly = false, tree = tree, preview = MovePreview("s-notes", null, result)))

        composeRule.onNodeWithText("/guide/setup.md → /notes/setup.md").assertExists()
        composeRule.onNodeWithText("Links change in 2 pages:").assertExists()
        composeRule.onAllNodesWithTag("move-edit-group").assertCountEquals(2)
        composeRule.onNodeWithText("guide/setup.md → notes/setup.md").assertExists()
        composeRule.onNodeWithText("+ [Setup](notes/setup.md)").assertExists()
        composeRule.onNodeWithTag("move-back").performClick()
        composeRule.onNodeWithTag("move-apply").performClick()
        assertEquals(1, back)
        assertEquals(1, applied)
    }

    @Test
    fun `a preview without edits says so`() {
        val result = MoveResultModel(true, "section", "s-notes", "s-n2", "notes", "guide/notes", emptyList(), emptyList(), emptyList())
        moveDialog(DialogState.Move(item = WikiItem.Section(notes), renameOnly = false, tree = tree, preview = MovePreview("s-guide", null, result)))
        composeRule.onNodeWithTag("move-no-edits").assertExists()
    }

    @Test
    fun `backlinks open their source document`() {
        val opened = mutableListOf<String>()
        val target = ItemRefModel("document", "d-setup", "guide/setup.md", "setup")
        val backlinks = listOf(
            BacklinkModel(ItemRefModel("document", "d-index", "index.md", "index"), "link", 3, 9, "guide/setup.md", "", target),
            BacklinkModel(ItemRefModel("document", "d-other", "notes/other.md", "other"), "link", 7, 2, "../guide/setup.md", "", target),
        )
        composeRule.setContent {
            MkDocsEditorTheme {
                BacklinksDialog(state = DialogState.Backlinks(setup, backlinks), onOpenDocument = { opened += it }, onDismissRequest = {})
            }
        }
        composeRule.onAllNodesWithTag("backlink").assertCountEquals(2)
        composeRule.onNodeWithText("Line 7 · ../guide/setup.md").assertExists()
        composeRule.onNodeWithText("notes/other.md").performClick()
        assertEquals(listOf("d-other"), opened)
    }

    @Test
    fun `no backlinks`() {
        composeRule.setContent {
            MkDocsEditorTheme {
                BacklinksDialog(state = DialogState.Backlinks(setup, emptyList()), onOpenDocument = {}, onDismissRequest = {})
            }
        }
        composeRule.onNodeWithTag("backlinks-none").assertExists()
    }

    @Test
    fun `older backends get an explanation instead of an error`() {
        moveDialog(DialogState.Move(item = setup, renameOnly = false, tree = tree, error = MoveOutcome.Failed(404, MoveOutcome.Failed.UNSUPPORTED, "Not Found")))
        composeRule.onNodeWithTag("move-error").assert(hasText("This server cannot move items yet.", substring = true))
    }

    @Test
    fun `backlinks on an older backend`() {
        composeRule.setContent {
            MkDocsEditorTheme {
                BacklinksDialog(state = DialogState.Backlinks(setup, unsupported = true), onOpenDocument = {}, onDismissRequest = {})
            }
        }
        composeRule.onNodeWithTag("backlinks-unsupported").assertExists()
    }
}
