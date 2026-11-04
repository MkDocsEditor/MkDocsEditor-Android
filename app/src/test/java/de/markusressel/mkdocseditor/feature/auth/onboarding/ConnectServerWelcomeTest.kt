package de.markusressel.mkdocseditor.feature.auth.onboarding

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectServerViewModel
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.compose.ConnectServerScreenContent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class WelcomeTestScreen(private val content: @Composable () -> Unit) : Screen {
    @Composable
    override fun Content() = content()
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class ConnectServerWelcomeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(firstRun: Boolean) {
        composeRule.setContent {
            MkDocsEditorTheme {
                Navigator(WelcomeTestScreen {
                    ConnectServerScreenContent(
                        uiState = ConnectServerViewModel.UiState(),
                        canGoBack = !firstRun,
                        onUiEvent = {},
                        onBackClicked = {},
                        firstRun = firstRun,
                    )
                })
            }
        }
    }

    @Test
    fun `the first start explains what a new user needs`() {
        show(firstRun = true)

        composeRule.onNodeWithText("Welcome").assertExists()
        composeRule.onNodeWithText("Ask your admin", substring = true).assertExists()
    }

    @Test
    fun `adding another server does not welcome again`() {
        show(firstRun = false)

        composeRule.onNodeWithText("Welcome").assertDoesNotExist()
    }
}
