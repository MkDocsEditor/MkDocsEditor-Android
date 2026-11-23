package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.compose.OnlineUsersAction
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteAgent
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class OnlineUsersActionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun agent(instanceId: String, selections: List<RemoteSelection>) = RemotePeer(
        clientId = "agent-$instanceId",
        username = "bob",
        displayName = "Bob",
        color = "#336699",
        selections = selections,
        accountId = "a1",
        instanceId = instanceId,
        agent = RemoteAgent(tokenId = "t-$instanceId", name = "claude-$instanceId"),
    )

    @Test
    fun `an agent with a focus can be followed, one without can not`() {
        val followed = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                OnlineUsersAction(
                    modifier = Modifier.testTag("users"),
                    remotePeers = listOf(agent("i1", listOf(RemoteSelection(3, 5))), agent("i2", emptyList())),
                    onFollowClicked = { followed += it },
                )
            }
        }
        composeRule.onNodeWithTag("users").performClick()

        composeRule.onNodeWithText("claude-i2 (agent of Bob)").assertIsNotEnabled()
        composeRule.onNodeWithText("Follow claude-i1 (agent of Bob)").assertIsEnabled().performClick()
        assertEquals(listOf("i1"), followed)
    }
}
