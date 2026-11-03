package de.markusressel.mkdocseditor.feature.filebrowser.ui

import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserViewModel.Companion.ROOT_SECTION_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SectionNavigatorTest {

    private val tree = MutableStateFlow(
        mapOf(
            "a" to listOf(section("a")),
            "b" to listOf(section("a"), section("b")),
            "c" to listOf(section("a"), section("b"), section("c")),
        )
    )

    private fun section(id: String, name: String = id) = SectionItem(id, name)
    private val rootItem = SectionItem(ROOT_SECTION_ID, "/")

    private fun navigator(start: String = ROOT_SECTION_ID) = SectionNavigator(
        initialSectionId = start,
        pathTo = { id ->
            tree.value[id]?.let { listOf(SectionItem(ROOT_SECTION_ID, "wiki")) + it }.orEmpty()
        },
        sectionChanges = { tree.map { } },
    )

    /** the latest path of a running navigator */
    private fun TestScope.observe(navigator: SectionNavigator): () -> List<SectionItem> {
        var latest = emptyList<SectionItem>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            navigator.path.collect { latest = it }
        }
        return { latest }
    }

    @Test
    fun `the root is always the only item at the top, even before the first sync`() = runTest {
        tree.value = emptyMap()
        val path = observe(navigator())

        assertEquals(listOf(rootItem), path())
    }

    @Test
    fun `the path goes from the root down to the open folder`() = runTest {
        val navigator = navigator()
        val path = observe(navigator)

        navigator.open("c")

        assertEquals(listOf(rootItem, section("a"), section("b"), section("c")), path())
    }

    @Test
    fun `a folder restored after the app was gone gets its path`() = runTest {
        val path = observe(navigator(start = "b"))

        assertEquals(listOf(rootItem, section("a"), section("b")), path())
    }

    @Test
    fun `a restored folder that is gone falls back to the root`() = runTest {
        val navigator = navigator(start = "gone")
        val path = observe(navigator)

        assertEquals(listOf(rootItem), path())
        assertEquals(ROOT_SECTION_ID, navigator.currentSectionId.value)
    }

    @Test
    fun `going up opens the parent until the root is reached`() = runTest {
        val navigator = navigator(start = "b")
        val path = observe(navigator)

        assertTrue(navigator.goUp())
        assertEquals("a", navigator.currentSectionId.value)
        assertTrue(navigator.goUp())
        assertEquals(listOf(rootItem), path())
        assertFalse(navigator.goUp())
    }

    @Test
    fun `a parent renamed elsewhere shows its new name`() = runTest {
        val path = observe(navigator(start = "b"))

        tree.value = mapOf("b" to listOf(section("a", "renamed"), section("b")))

        assertEquals(listOf(rootItem, section("a", "renamed"), section("b")), path())
    }

    @Test
    fun `when the open folder is gone the nearest parent opens`() = runTest {
        val navigator = navigator(start = "c")
        val path = observe(navigator)

        tree.value = tree.value - "c"

        assertEquals("b", navigator.currentSectionId.value)
        assertEquals(listOf(rootItem, section("a"), section("b")), path())
    }

    @Test
    fun `when the open folder and its parents are gone the root opens`() = runTest {
        val navigator = navigator(start = "c")
        val path = observe(navigator)

        tree.value = emptyMap()

        assertEquals(ROOT_SECTION_ID, navigator.currentSectionId.value)
        assertEquals(listOf(rootItem), path())
    }
}
