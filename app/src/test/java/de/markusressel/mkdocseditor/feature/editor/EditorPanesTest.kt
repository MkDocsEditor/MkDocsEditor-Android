package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.compose.EDITOR_MODE_ANIMATION_MS
import de.markusressel.mkdocseditor.feature.editor.ui.compose.EditorPanes
import de.markusressel.mkdocseditor.feature.editor.ui.compose.MAX_SPLIT_RATIO
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SPLIT_DIVIDER_TAG
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitMode
import de.markusressel.mkdocseditor.feature.editor.ui.compose.defaultSplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w400dp-h800dp")
class EditorPanesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var finished by mutableIntStateOf(0)

    @Composable
    private fun Panes(
        showEditor: Boolean = true,
        showPreview: Boolean = true,
        mode: SplitMode,
        ratio: Float = 0.5f,
        onRatioChange: (Float) -> Unit = {},
        editorPreviewContent: @Composable () -> Unit = { Box(Modifier.testTag(EDITOR).fillMaxSize()) },
    ) {
        Box(Modifier.size(400.dp, 800.dp)) {
            EditorPanes(
                showEditor = showEditor,
                showPreview = showPreview,
                splitMode = mode,
                ratio = ratio,
                onRatioChange = onRatioChange,
                onRatioChangeFinished = { finished++ },
                editor = editorPreviewContent,
                preview = { Box(Modifier.testTag(PREVIEW).fillMaxSize()) },
            )
        }
    }

    @Test
    fun `reading shows only the page, over the whole space`() {
        composeRule.setContent { Panes(showEditor = false, mode = SplitMode.Vertical) }

        composeRule.onNodeWithTag(EDITOR).assertDoesNotExist()
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(PREVIEW).assertWidthIsEqualTo(400.dp).assertHeightIsEqualTo(800.dp)
    }

    @Test
    fun `without a split the editor has the whole space`() {
        composeRule.setContent { Panes(showPreview = false, mode = SplitMode.Off) }

        // without room, but ready for when it comes back
        composeRule.onNodeWithTag(PREVIEW).assertHeightIsEqualTo(0.dp)
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(EDITOR).assertWidthIsEqualTo(400.dp).assertHeightIsEqualTo(800.dp)
    }

    @Test
    fun `vertical puts the editor above the page and uses the ratio`() {
        composeRule.setContent { Panes(mode = SplitMode.Vertical, ratio = 0.25f) }

        composeRule.onNodeWithTag(EDITOR).assertWidthIsEqualTo(400.dp).assertHeightIsEqualTo(200.dp)
        composeRule.onNodeWithTag(PREVIEW).assertWidthIsEqualTo(400.dp).assertHeightIsEqualTo(600.dp)
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TAG).assertIsDisplayed()
    }

    @Test
    fun `horizontal puts the editor next to the page and uses the ratio`() {
        composeRule.setContent { Panes(mode = SplitMode.Horizontal, ratio = 0.75f) }

        composeRule.onNodeWithTag(EDITOR).assertWidthIsEqualTo(300.dp).assertHeightIsEqualTo(800.dp)
        composeRule.onNodeWithTag(PREVIEW).assertWidthIsEqualTo(100.dp).assertHeightIsEqualTo(800.dp)
    }

    @Test
    fun `dragging the divider changes the ratio and stops at the limit`() {
        var ratio by mutableFloatStateOf(0.5f)
        composeRule.setContent { Panes(mode = SplitMode.Horizontal, ratio = ratio, onRatioChange = { ratio = it }) }

        composeRule.onNodeWithTag(SPLIT_DIVIDER_TAG).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 2000f)
        }
        composeRule.waitForIdle()

        assertEquals(MAX_SPLIT_RATIO, ratio, 0.001f)
        assertEquals(1, finished)
    }

    @Test
    fun `changing the mode keeps the focus of the editor`() {
        var mode by mutableStateOf(SplitMode.Off)
        composeRule.setContent {
            Panes(showPreview = mode != SplitMode.Off, mode = mode) {
                Box(Modifier.testTag(EDITOR).fillMaxSize().focusable())
            }
        }
        val editor = composeRule.onNodeWithTag(EDITOR)
        editor.performSemanticsAction(SemanticsActions.RequestFocus)
        editor.assertIsFocused()

        SplitMode.entries.forEach {
            mode = it
            composeRule.waitForIdle()
            editor.assertIsFocused()
        }
        mode = SplitMode.Vertical
        composeRule.waitForIdle()
        editor.assertIsFocused()
    }

    @Test
    fun `changing the mode moves the panes over time`() {
        var editing by mutableStateOf(false)
        composeRule.setContent { Panes(showEditor = editing, showPreview = !editing, mode = SplitMode.Off) }
        composeRule.onNodeWithTag(PREVIEW).assertHeightIsEqualTo(800.dp)

        composeRule.mainClock.autoAdvance = false
        editing = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(EDITOR_MODE_ANIMATION_MS / 2L)
        val editorHeight = composeRule.onNodeWithTag(EDITOR).getUnclippedBoundsInRoot().height
        assertTrue("the editor is at $editorHeight", editorHeight > 0.dp && editorHeight < 800.dp)
        composeRule.onNodeWithTag(PREVIEW).assertExists()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(EDITOR).assertHeightIsEqualTo(800.dp)
        composeRule.onNodeWithTag(PREVIEW).assertHeightIsEqualTo(0.dp)
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TAG).assertDoesNotExist()
    }

    @Test
    fun `the mode goes through all three states`() {
        assertEquals(SplitMode.Vertical, SplitMode.Off.next())
        assertEquals(SplitMode.Horizontal, SplitMode.Vertical.next())
        assertEquals(SplitMode.Off, SplitMode.Horizontal.next())
    }

    @Test
    fun `only a window with room for both panes splits on its own`() {
        // phone, phone in landscape, open foldable, closed foldable
        assertEquals(SplitMode.Off, defaultSplitMode(widthDp = 411, heightDp = 891))
        assertEquals(SplitMode.Off, defaultSplitMode(widthDp = 891, heightDp = 411))
        assertEquals(SplitMode.Horizontal, defaultSplitMode(widthDp = 840, heightDp = 900))
        assertTrue(defaultSplitMode(widthDp = 344, heightDp = 882) == SplitMode.Off)
    }

    private companion object {
        const val EDITOR = "editor"
        const val PREVIEW = "preview"
    }
}
