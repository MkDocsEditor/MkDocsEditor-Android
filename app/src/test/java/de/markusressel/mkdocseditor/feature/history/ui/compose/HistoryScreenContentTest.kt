package de.markusressel.mkdocseditor.feature.history.ui.compose

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.DiffState
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.UndoCheck
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.UiState
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocsrestclient.history.DiffHunkModel
import de.markusressel.mkdocsrestclient.history.DiffLineModel
import de.markusressel.mkdocsrestclient.history.DiffOp
import de.markusressel.mkdocsrestclient.history.HistoryAgentModel
import de.markusressel.mkdocsrestclient.history.HistoryAuthorModel
import de.markusressel.mkdocsrestclient.history.HistoryEntryModel
import de.markusressel.mkdocsrestclient.history.HistorySummaryModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class HistoryScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val anna = HistoryAuthorModel("anna", "anna@mkdocseditor.invalid", "anna", "a1", "Anna", "#1971c2")
    private val bob = HistoryAuthorModel("bob", "bob@mkdocseditor.invalid", "bob", "a2", "Bob")
    private val hunk = DiffHunkModel(
        heading = "Install", headingOccurrence = 1, oldStart = 3, oldLines = 1, newStart = 3, newLines = 1,
        lines = listOf(DiffLineModel(DiffOp.REMOVED, "old line"), DiffLineModel(DiffOp.ADDED, "new line")),
    )
    private val entries = listOf(
        HistoryEntryModel(
            revision = "b", time = Date(), author = anna, coAuthors = listOf(bob), message = "Update a.md", path = "a.md",
            agents = listOf(HistoryAgentModel("Claude Code", "a2")), summary = HistorySummaryModel(1, 1, listOf("Install")),
        ),
        HistoryEntryModel(revision = "a", time = Date(0), author = anna, message = "Restore a.md to 0123456789ab", path = "a.md"),
    )

    /** the top bar goes back through the navigator, so the content needs one */
    @Composable
    private fun Shown(content: @Composable () -> Unit) {
        val screen = remember {
            object : Screen {
                override val key = "history"

                @Composable
                override fun Content() = MkDocsEditorTheme { content() }
            }
        }
        Navigator(screen)
    }

    @Test
    fun `an entry names its people and agents, its changes and an unusual message`() {
        composeRule.setContent {
            Shown { HistoryScreenContent(UiState(loading = false, entries = entries), onUiEvent = {}) }
        }

        composeRule.onNodeWithText("Anna, Bob via Claude Code").assertExists()
        composeRule.onNodeWithText("Anna via Claude Code, Bob").assertDoesNotExist()
        composeRule.onNodeWithText("+1 −1 · Install", substring = true).assertExists()
        composeRule.onNodeWithText("Restore a.md to 0123456789ab").assertExists()
        composeRule.onNodeWithText("Update a.md").assertDoesNotExist()
    }

    @Test
    fun `an open entry shows its hunks, and a hunk opens the editor`() {
        val events = mutableListOf<UiEvent>()
        composeRule.setContent {
            Shown {
                HistoryScreenContent(
                    UiState(loading = false, entries = entries, expanded = "a", diffs = mapOf("a" to DiffState.Loaded(listOf(hunk))), canRestore = true),
                    onUiEvent = { events += it },
                )
            }
        }

        composeRule.onNodeWithText("+ new line").assertExists()
        composeRule.onNodeWithText("− old line").assertExists()
        composeRule.onNodeWithText("Install").performClick()
        composeRule.onNodeWithText("Restore this version").performClick()

        assertEquals(listOf(UiEvent.HunkClicked(hunk), UiEvent.RestoreClicked(entries[1])), events)
    }

    @Test
    fun `editors on a server with revert can undo a change, but not the first version`() {
        val events = mutableListOf<UiEvent>()
        composeRule.setContent {
            Shown {
                HistoryScreenContent(
                    UiState(
                        loading = false, entries = entries, expanded = "b",
                        diffs = mapOf("b" to DiffState.Loaded(listOf(hunk)), "a" to DiffState.FirstVersion), canRevert = true,
                    ),
                    onUiEvent = { events += it },
                )
            }
        }

        composeRule.onNodeWithText("Undo this change").performClick()
        assertEquals(listOf<UiEvent>(UiEvent.UndoClicked(entries[0])), events)
    }

    @Test
    fun `the first version can not be undone`() {
        composeRule.setContent {
            Shown {
                HistoryScreenContent(
                    UiState(loading = false, entries = entries, expanded = "a", diffs = mapOf("a" to DiffState.FirstVersion), canRevert = true),
                    onUiEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("Undo this change").assertDoesNotExist()
    }

    @Test
    fun `the newest entry can not be restored, it is the text as it is`() {
        composeRule.setContent {
            Shown {
                HistoryScreenContent(
                    UiState(loading = false, entries = entries, expanded = "b", diffs = mapOf("b" to DiffState.Loaded(listOf(hunk))), canRestore = true),
                    onUiEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("Restore this version").assertDoesNotExist()
    }

    @Test
    fun `a hunk of an older version can restore its section, but not above the first heading`() {
        val top = hunk.copy(heading = null, lines = listOf(DiffLineModel(DiffOp.ADDED, "intro")))
        val older = entries + HistoryEntryModel(revision = "0", time = Date(0), author = anna, message = "Initial", path = "a.md")
        val events = mutableListOf<UiEvent>()
        composeRule.setContent {
            Shown {
                HistoryScreenContent(
                    UiState(
                        loading = false, entries = older, expanded = "a",
                        diffs = mapOf("a" to DiffState.Loaded(listOf(top, hunk))), canRestoreSection = true,
                    ),
                    onUiEvent = { events += it },
                )
            }
        }

        // below the small test window, so clicked through its semantics
        composeRule.onNodeWithText("Restore “Install”").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf<UiEvent>(UiEvent.RestoreSectionClicked(older[1], hunk)), events)
        composeRule.onNodeWithText("Restore “”").assertDoesNotExist()
    }

    @Test
    fun `the newest entry has no section to restore`() {
        composeRule.setContent {
            Shown {
                HistoryScreenContent(
                    UiState(loading = false, entries = entries, expanded = "b", diffs = mapOf("b" to DiffState.Loaded(listOf(hunk))), canRestoreSection = true),
                    onUiEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("Restore “Install”").assertDoesNotExist()
    }

    @Test
    fun `a change that can't be undone says why instead of offering the undo`() {
        composeRule.setContent {
            Shown {
                HistoryScreenContent(
                    UiState(
                        loading = false, entries = entries, expanded = "b", diffs = mapOf("b" to DiffState.Loaded(listOf(hunk))),
                        canRevert = true, undoChecks = mapOf("b" to UndoCheck.Conflict(listOf("Install"))),
                    ),
                    onUiEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("Can't undo: later changes in Install are in the way.").assertExists()
        composeRule.onNodeWithText("Undo this change").assertDoesNotExist()
    }

    @Test
    fun `a change undone already says so`() {
        composeRule.setContent {
            Shown {
                HistoryScreenContent(
                    UiState(
                        loading = false, entries = entries, expanded = "b", diffs = mapOf("b" to DiffState.Loaded(listOf(hunk))),
                        canRevert = true, undoChecks = mapOf("b" to UndoCheck.Gone),
                    ),
                    onUiEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("Can't undo: this change was undone already or edited away.").assertExists()
        composeRule.onNodeWithText("Undo this change").assertDoesNotExist()
    }
}
