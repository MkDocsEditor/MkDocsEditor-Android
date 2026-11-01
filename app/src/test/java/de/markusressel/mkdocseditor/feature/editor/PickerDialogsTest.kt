package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.LinkPickerDialog
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.ResourcePickerDialog
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.doc
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.res
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
// a phone sized screen, so the lists show all entries
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class PickerDialogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * root: index.md, logo.png, notes.pdf
     *   Rezepte: Apfelkuchen.md (the open page), Kürbissuppe.md
     *   Urlaub: Strand.png
     */
    private val rezepte = section("s-r", "Rezepte", documents = listOf(doc("d-apfel", "Apfelkuchen"), doc("d-suppe", "Kürbissuppe")))
    private val urlaub = section("s-u", "Urlaub", resources = listOf(res("r-strand", "Strand.png")))
    private val tree = WikiTree(
        section(
            "root", "root",
            subsections = listOf(rezepte, urlaub),
            documents = listOf(doc("d-index", "index")),
            resources = listOf(res("r-logo", "logo.png"), res("r-pdf", "notes.pdf")),
        )
    )

    private val inserted = mutableListOf<String>()
    private val uploads = mutableListOf<Triple<String, List<Uri>, String>>()

    private fun linkPicker(selection: String = "") = composeRule.setContent {
        MkDocsEditorTheme {
            LinkPickerDialog(
                tree = tree,
                documentId = "d-apfel",
                documentText = "# Apfelkuchen\n## Teig",
                selection = selection,
                textLoader = { id -> if (id == "d-suppe") "# Kürbissuppe\n\n## Zutaten\n\n## Zubereitung" else null },
                thumbnailLoader = null,
                onInsert = { inserted += it },
                onDismiss = {},
            )
        }
    }

    private fun resourcePicker(imagesOnly: Boolean = true, selection: String = "") = composeRule.setContent {
        MkDocsEditorTheme {
            ResourcePickerDialog(
                tree = tree,
                documentId = "d-apfel",
                selection = selection,
                imagesOnly = imagesOnly,
                uploading = false,
                thumbnailLoader = null,
                photoUriFactory = null,
                onInsert = { inserted += it },
                onUpload = { section, uris, alt -> uploads += Triple(section, uris, alt) },
                onDismiss = {},
            )
        }
    }

    @Test
    fun `a heading of another page`() {
        linkPicker()
        composeRule.onNodeWithText("Kürbissuppe").performClick()
        composeRule.onNodeWithText("Zutaten").performClick()
        assertEquals(listOf("[Zutaten](Kürbissuppe.md#zutaten)"), inserted)
    }

    @Test
    fun `the whole page with the selection as link text`() {
        linkPicker(selection = "die Suppe")
        composeRule.onNodeWithText("Kürbissuppe").performClick()
        composeRule.onNodeWithTag("link-page").performClick()
        assertEquals(listOf("[die Suppe](Kürbissuppe.md)"), inserted)
    }

    @Test
    fun `a heading of the open page`() {
        linkPicker()
        composeRule.onNodeWithText("Apfelkuchen").performClick()
        composeRule.onNodeWithText("This page").assertExists()
        composeRule.onNodeWithText("Teig").performClick()
        assertEquals(listOf("[Teig](#teig)"), inserted)
    }

    @Test
    fun `search finds files in other folders`() {
        linkPicker()
        composeRule.onNodeWithTag("browser-search").performTextInput("strand")
        composeRule.onNodeWithText("Strand.png").performClick()
        assertEquals(listOf("[Strand.png](../Urlaub/Strand.png)"), inserted)
    }

    @Test
    fun `a folder`() {
        linkPicker()
        composeRule.onNodeWithTag("browser-up").performClick()
        composeRule.onNodeWithText("Urlaub").performClick()
        composeRule.onNodeWithTag("browser-pick-current").performClick()
        assertEquals(listOf("[Urlaub](../Urlaub/)"), inserted)
    }

    @Test
    fun `a selected web address opens the web tab`() {
        linkPicker(selection = "https://example.com/a b")
        composeRule.onNodeWithTag("link-text").performTextInput("Example")
        composeRule.onNodeWithTag("link-insert-web").performClick()
        assertEquals(listOf("[Example](https://example.com/a%20b)"), inserted)
    }

    @Test
    fun `images only, then all files`() {
        resourcePicker()
        composeRule.onNodeWithTag("browser-up").performClick()
        composeRule.onAllNodesWithTag("browser-resource").assertCountEquals(1)
        composeRule.onNodeWithTag("resource-images-only").performClick()
        composeRule.onAllNodesWithTag("browser-resource").assertCountEquals(2)
        composeRule.onNodeWithText("notes.pdf").performClick()
        assertEquals(listOf("[notes.pdf](../notes.pdf)"), inserted)
    }

    @Test
    fun `an image with alt text`() {
        resourcePicker(selection = "Der Strand")
        composeRule.onNodeWithTag("browser-up").performClick()
        composeRule.onNodeWithText("Urlaub").performClick()
        composeRule.onNodeWithText("Strand.png").performClick()
        assertEquals(listOf("![Der Strand](../Urlaub/Strand.png)"), inserted)
    }

    @Test
    fun `uploads go into the page's folder by default`() {
        resourcePicker()
        composeRule.onNodeWithTag("tab-upload").performClick()
        composeRule.onNodeWithText("Upload into: Rezepte").assertExists()
        composeRule.onNodeWithTag("browser-up").performClick()
        composeRule.onNodeWithText("Upload into: Wiki (top folder)").assertExists()
    }

    @Test
    fun `an image from the web`() {
        resourcePicker()
        composeRule.onNodeWithTag("tab-web").performClick()
        composeRule.onNodeWithTag("resource-url").performTextInput("https://example.com/cat.png")
        composeRule.onNodeWithTag("resource-insert-web").performClick()
        assertEquals(listOf("![cat.png](https://example.com/cat.png)"), inserted)
    }
}
