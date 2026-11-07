package de.markusressel.mkdocseditor.feature.backendconfig.edit

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.displayAddress
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel.UiState
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose.BackendConfigEditScreenContent
import de.markusressel.mkdocseditor.feature.auth.common.data.ParsedBackendUrl
import de.markusressel.mkdocseditor.feature.editor.domain.hostWithPort
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class EditTestScreen(private val content: @Composable () -> Unit) : Screen {
    @Composable
    override fun Content() = content()
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class BackendConfigEditContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<UiEvent>()

    private val config = BackendConfig(
        id = 1,
        name = "Wiki",
        description = "",
        isSelected = true,
        serverConfig = null,
        mkDocsWebConfig = null,
    )

    private fun show(state: UiState) {
        composeRule.setContent {
            MkDocsEditorTheme {
                Navigator(EditTestScreen { BackendConfigEditScreenContent(uiState = state, onUiEvent = { events += it }) })
            }
        }
    }

    @Test
    fun `an invalid address cannot be saved`() {
        show(UiState(config = config, name = "Wiki", address = "not an address"))

        composeRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a valid address can be saved`() {
        show(UiState(config = config, name = "Wiki", address = "wiki.example.org"))

        composeRule.onNodeWithText("Save").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `removing a server asks first`() {
        show(UiState(config = config, name = "Wiki", confirmingDelete = true))

        composeRule.onNodeWithText("Remove Wiki?").assertExists()
    }

    @Test
    fun `the remove button only opens the question`() {
        show(UiState(config = config, name = "Wiki"))

        composeRule.onNodeWithText("Remove server").performScrollTo().performClick()

        assertEquals(listOf<UiEvent>(UiEvent.DeleteClicked), events)
    }

    @Test
    fun `a stored address reads back the same`() {
        listOf(
            Triple("wiki.example.org", 443, true),
            Triple("wiki.example.org", 8123, true),
            Triple("192.168.2.20", 7413, false),
            Triple("example.org/api", 80, false),
        ).forEach { (domain, port, ssl) ->
            val parsed = ParsedBackendUrl.parse(displayAddress(domain, port, ssl))

            assertEquals(ParsedBackendUrl(domain, port, ssl), parsed)
        }
    }

    @Test
    fun `the usual port does not show`() {
        assertEquals("https://wiki.example.org", displayAddress("wiki.example.org", 443, true))
        assertEquals("", displayAddress("", 80, false))
    }

    @Test
    fun `the web port is used, except the 80 of the old form with https`() {
        assertEquals("a.org", hostWithPort("a.org", 443, true))
        assertEquals("a.org", hostWithPort("a.org", 80, true))
        assertEquals("a.org:8443", hostWithPort("a.org", 8443, true))
        assertEquals("a.org:8000/wiki", hostWithPort("a.org/wiki", 8000, false))
    }
}
