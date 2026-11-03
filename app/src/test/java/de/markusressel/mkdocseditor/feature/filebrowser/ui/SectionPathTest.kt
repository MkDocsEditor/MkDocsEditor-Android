package de.markusressel.mkdocseditor.feature.filebrowser.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.SectionPath
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w320dp-h640dp")
class SectionPathTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clicked = mutableListOf<String>()
    private var path by mutableStateOf(emptyList<SectionItem>())

    private fun items(vararg names: String) = listOf(SectionItem("root", "/")) + names.map { SectionItem(it, it) }

    private fun show(items: List<SectionItem>) {
        path = items
        composeRule.setContent {
            MkDocsEditorTheme {
                SectionPath(
                    modifier = Modifier.fillMaxWidth(),
                    path = path,
                    onSectionClicked = { clicked += it.id },
                )
            }
        }
    }

    private val notClickable = SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)

    @Test
    fun `a parent can be clicked`() {
        show(items("guide", "setup"))

        composeRule.onNode(hasText("guide")).performClick()
        composeRule.onNodeWithContentDescription("Top level of the wiki").performClick()

        assertEquals(listOf("guide", "root"), clicked)
    }

    @Test
    fun `the open folder is not a button`() {
        show(items("guide", "setup"))

        composeRule.onNode(hasText("setup").and(hasClickAction())).assertDoesNotExist()
    }

    @Test
    fun `at the top the root is the open folder`() {
        show(items())

        composeRule.onNode(hasText("/").and(notClickable), useUnmergedTree = true).assertExists()
        composeRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `a deep path shows the open folder`() {
        val deep = (1..12).map { "folder$it" }.toTypedArray()
        show(items(*deep))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("folder12").assertIsDisplayed()
        composeRule.onNodeWithText("folder1").assertIsNotDisplayed()
    }

    @Test
    fun `going deeper scrolls to the new open folder`() {
        val deep = (1..11).map { "folder$it" }
        show(items(*deep.take(2).toTypedArray()))
        composeRule.waitForIdle()

        path = items(*deep.toTypedArray())
        composeRule.waitForIdle()

        composeRule.onNodeWithText("folder11").assertIsDisplayed()
    }

    @Test
    fun `an empty path shows no chip and does not fail`() {
        show(emptyList())

        composeRule.onNode(hasClickAction()).assertDoesNotExist()
    }
}
