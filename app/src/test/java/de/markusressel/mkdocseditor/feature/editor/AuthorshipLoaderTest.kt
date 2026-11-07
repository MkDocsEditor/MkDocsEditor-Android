package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.feature.editor.ui.AuthorshipLoader
import de.markusressel.mkdocsrestclient.reference.AuthorshipModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AuthorshipLoaderTest {

    /** the times of the requests */
    private val requests = mutableListOf<Long>()

    /** whether an answer fits the text */
    private var fits = true

    /** a change of the text, like `authorshipRequests` of the view model */
    private val changes = MutableStateFlow(0)

    private fun TestScope.start() {
        val loader = AuthorshipLoader(
            fetch = {
                requests += testScheduler.currentTime
                AuthorshipModel()
            },
            show = { fits },
            now = { testScheduler.currentTime },
        )
        backgroundScope.launch { changes.collectLatest { loader.load("doc") } }
        runCurrent()
    }

    /** a key press every [everyMs] until [untilMs] */
    private fun TestScope.typeUntil(untilMs: Long, everyMs: Long = 500) {
        while (testScheduler.currentTime + everyMs <= untilMs) {
            advanceTimeBy(everyMs)
            runCurrent()
            changes.value++
            runCurrent()
        }
    }

    @Test
    fun `the backend is asked once the text has settled`() = runTest {
        start()
        typeUntil(1500)

        advanceTimeBy(1999)
        runCurrent()
        assertEquals(emptyList(), requests)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(3500L), requests)
    }

    @Test
    fun `typing without a pause asks the backend every 10 s`() = runTest {
        fits = false
        start()

        typeUntil(25_000)

        assertEquals(listOf(10_000L, 20_000L), requests)
    }

    @Test
    fun `an answer that does not fit is asked for again after the next settle`() = runTest {
        fits = false
        start()

        advanceTimeBy(20_000)
        runCurrent()

        // three attempts while the text stays as it is, then nothing until it changes
        assertEquals(listOf(2000L, 4000L, 6000L), requests)
    }
}
