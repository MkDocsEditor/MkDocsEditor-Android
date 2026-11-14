package de.markusressel.mkdocseditor.feature.history.ui.compose

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocsrestclient.history.ChangeAuthorModel
import de.markusressel.mkdocsrestclient.history.DocumentChangesModel
import de.markusressel.mkdocsrestclient.history.HistorySummaryModel
import de.markusressel.mkdocsrestclient.history.SeenMarkModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class ChangesSinceSeenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val changes = DocumentChangesModel(
        since = SeenMarkModel(Date(0), "a"),
        seenToken = "b",
        authors = listOf(
            ChangeAuthorModel(displayName = "Alice"),
            ChangeAuthorModel(displayName = "Bob", agents = listOf("Claude Code")),
        ),
        summary = HistorySummaryModel(2, 1, listOf("Setup", "Install")),
    )

    @Test
    fun `the banner names who changed the page and where, and opens or hides`() {
        val clicks = mutableListOf<String>()
        composeRule.setContent {
            MkDocsEditorTheme {
                ChangesSinceSeenBanner(changes, onClick = { clicks += "open" }, onDismiss = { clicks += "hide" })
            }
        }

        composeRule.onNodeWithText("Changed by Alice, Bob via Claude Code since you were here").performClick()
        composeRule.onNodeWithText("Setup, Install").assertExists()
        composeRule.onNodeWithContentDescription("Hide").performClick()

        assertEquals(listOf("open", "hide"), clicks)
    }
}
