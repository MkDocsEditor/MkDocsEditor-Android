package de.markusressel.mkdocseditor.feature.history.ui.compose

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocsrestclient.dashboard.FeedAuthorModel
import de.markusressel.mkdocsrestclient.history.BlameBlockModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class LineAuthorSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bob = FeedAuthorModel(accountId = "a2", username = "bob", displayName = "Bob", color = "#2f9e44")

    @Test
    fun `committed lines name their author, the time and a message of its own`() {
        var historyOpened = 0
        val block = BlameBlockModel(
            start = 7, end = 9, revision = "r", time = Date(), message = "Restore section \"Install\" of a.md to bde4a3c",
            author = bob,
        )
        composeRule.setContent { MkDocsEditorTheme { LineAuthorContent(block, onOpenHistory = { historyOpened++ }) } }

        composeRule.onNodeWithText("Lines 7–9").assertExists()
        composeRule.onNodeWithText("Bob").assertExists()
        composeRule.onNodeWithText("just now").assertExists()
        composeRule.onNodeWithText("Restore section \"Install\" of a.md to bde4a3c").assertExists()
        composeRule.onNodeWithText("History").performClick()
        assertEquals(1, historyOpened)
    }

    @Test
    fun `pending lines of an agent have no time, and the standard message is left out`() {
        val pending = BlameBlockModel(start = 12, end = 12, pending = true, author = bob, agents = listOf("Claude Code"))
        val standard = BlameBlockModel(start = 1, end = 1, revision = "r", time = Date(0), message = "Update guide/setup.md", author = bob)
        composeRule.setContent {
            MkDocsEditorTheme {
                LineAuthorContent(pending, onOpenHistory = null)
                LineAuthorContent(standard, onOpenHistory = null)
            }
        }

        composeRule.onNodeWithText("Line 12").assertExists()
        composeRule.onNodeWithText("Bob via Claude Code").assertExists()
        composeRule.onNodeWithText("Not saved in the history yet").assertExists()
        composeRule.onNodeWithText("Update guide/setup.md").assertDoesNotExist()
        composeRule.onNodeWithText("History").assertDoesNotExist()
    }
}
