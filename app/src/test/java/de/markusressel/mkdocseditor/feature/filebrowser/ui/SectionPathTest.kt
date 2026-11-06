package de.markusressel.mkdocseditor.feature.filebrowser.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.FOLDER_ANIMATION_MS
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.SectionPath
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w320dp-h640dp")
// a chip that fades out is no longer in the semantics tree, only in the drawn pixels
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
                    modifier = Modifier.fillMaxWidth().testTag("bar"),
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
        // the list only composes what fits on the screen
        composeRule.onNodeWithText("folder1").assertDoesNotExist()
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
    fun `a folder that was left fades out`() {
        show(items("guide"))
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        path = items()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("guide").assertExists()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("guide").assertDoesNotExist()
    }

    @Test
    fun `going one folder deeper in a long path shows the new folder`() {
        val deep = (1..12).map { "folder$it" }
        show(items(*deep.dropLast(1).toTypedArray()))
        composeRule.waitForIdle()

        path = items(*deep.toTypedArray())
        composeRule.waitForIdle()

        val bar = composeRule.onNodeWithTag("bar").getBoundsInRoot()
        val chip = composeRule.onNodeWithText("folder12").getBoundsInRoot()
        assertTrue("$chip ends in $bar", chip.right <= bar.right && chip.left >= bar.left)
    }

    @Test
    fun `the last folder fades out when going up from a deeper one`() {
        show(items("guide"))
        composeRule.waitForIdle()
        // the folder that is left was opened in the app, which scrolls the path
        path = items("guide", "setup")
        composeRule.waitForIdle()
        val chip = composeRule.onNodeWithText("setup").getBoundsInRoot()

        /** whether anything but the background of the bar is drawn where the chip was */
        fun chipIsDrawn(): Boolean {
            val image = composeRule.onNodeWithTag("bar").captureToImage().toPixelMap()
            val background = image[1, 1]
            val y = with(composeRule.density) { ((chip.top + chip.bottom) / 2).roundToPx() }
            val xs = with(composeRule.density) { chip.left.roundToPx() until chip.right.roundToPx() }
            return xs.any { image[it, y] != background }
        }
        assertTrue(chipIsDrawn())

        composeRule.mainClock.autoAdvance = false
        path = items("guide")
        // frame by frame, so that each frame is laid out as on a device
        repeat((FOLDER_ANIMATION_MS / 2) / 16) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            assertTrue("the chip vanished before it faded out", chipIsDrawn())
        }

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertFalse(chipIsDrawn())
    }

    @Test
    fun `an empty path shows no chip and does not fail`() {
        show(emptyList())

        composeRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `the bar and the root stay in place during the animation`() {
        show(items())
        composeRule.waitForIdle()
        fun bar() = composeRule.onNodeWithTag("bar").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        fun root() = composeRule.onNode(hasText("/"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val height = bar()
        val rootLeft = root().left

        listOf(items("guide"), items()).forEach { next ->
            composeRule.mainClock.autoAdvance = false
            path = next
            repeat(30) {
                composeRule.mainClock.advanceTimeByFrame()
                assertEquals(height, bar())
                assertEquals(rootLeft, root().left)
            }
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
        }
    }
}
