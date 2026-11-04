package de.markusressel.mkdocseditor.feature.filebrowser.ui

import de.markusressel.mkdocseditor.feature.common.ui.compose.SyncAge
import de.markusressel.mkdocseditor.feature.common.ui.compose.syncAge
import java.util.Date
import java.util.concurrent.TimeUnit
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.editor.documentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.FileBrowserScreenContent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class ErrorTestScreen(private val content: @Composable () -> Unit) : Screen {
    @Composable
    override fun Content() = content()
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class FileBrowserErrorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<UiEvent>()

    private fun show(state: UiState) {
        composeRule.setContent {
            MkDocsEditorTheme {
                Navigator(ErrorTestScreen { FileBrowserScreenContent(uiState = state, onUiEvent = { events += it }) })
            }
        }
    }

    @Test
    fun `a folder that could not be loaded shows the failure and a retry in its place`() {
        show(UiState(folderError = R.string.error_unreachable))

        composeRule.onNodeWithText("Can't reach the server. Check your connection and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf<UiEvent>(UiEvent.Refresh), events)
    }

    @Test
    fun `a failed refresh keeps the content and offers a retry`() {
        show(
            UiState(
                folderContent = FolderContent(sectionId = "a", documents = listOf(documentData("kept"))),
                error = ScreenError(R.string.error_unreachable, canRetry = true),
            )
        )

        composeRule.onNodeWithText("kept").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<UiEvent>(UiEvent.Refresh), events)
    }

    @Test
    fun `a failed action has no retry, because that would only reload the folder`() {
        show(UiState(error = ScreenError(R.string.error_server, canRetry = false)))

        composeRule.onNodeWithText("The server has a problem. Please try again later.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `the top bar says how long ago the backend answered`() {
        show(UiState(lastSync = Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3))))

        composeRule.onNodeWithText("Synced 3 hours ago").assertIsDisplayed()
    }

    @Test
    fun `the top bar says when the backend did not answer`() {
        show(UiState(lastSync = Date(), lastSyncFailed = true))
        composeRule.onNodeWithText("Not connected · Synced just now").assertIsDisplayed()
    }

    @Test
    fun `without an answer so far and without a failure the top bar has no status`() {
        show(UiState())

        composeRule.onNodeWithText("Synced", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Not connected", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the age of a sync uses the largest fitting unit`() {
        val now = Date(TimeUnit.DAYS.toMillis(100))
        fun ago(minutes: Long) = syncAge(Date(now.time - TimeUnit.MINUTES.toMillis(minutes)), now)

        assertEquals(null, ago(0))
        assertEquals(SyncAge(R.plurals.synced_minutes, 59), ago(59))
        assertEquals(SyncAge(R.plurals.synced_hours, 1), ago(60))
        assertEquals(SyncAge(R.plurals.synced_days, 2), ago(48 * 60))
    }

    @Test
    fun `a load failure is only quiet when stored content shows and nobody is waiting`() {
        assertEquals(LoadFailurePlace.Nowhere, placeOfLoadFailure(contentShown = true, userAsked = false))
        assertEquals(LoadFailurePlace.Snackbar, placeOfLoadFailure(contentShown = true, userAsked = true))
        assertEquals(LoadFailurePlace.InPlaceOfContent, placeOfLoadFailure(contentShown = false, userAsked = false))
        assertEquals(LoadFailurePlace.InPlaceOfContent, placeOfLoadFailure(contentShown = false, userAsked = true))
    }
}
