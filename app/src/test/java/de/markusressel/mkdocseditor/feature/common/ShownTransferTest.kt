package de.markusressel.mkdocseditor.feature.common

import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import de.markusressel.mkdocseditor.feature.common.ui.ShownTransfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ShownTransferTest {

    /** what the screen shows */
    private val shown = MutableStateFlow<TransferProgress?>(null)

    /** with a supervisor, like the scope of a ViewModel: a failed transfer does not end the others */
    private fun TestScope.shownTransfer() = ShownTransfer(
        scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job)),
    ) { change -> shown.update(change) }

    /** a download that reports its start, and then waits for [finish] */
    private class FakeDownload(name: String) {
        val progress = TransferProgress.start(name, upload = false)
        val finish = CompletableDeferred<String>()

        suspend fun run(onProgress: (TransferProgress) -> Unit): String {
            onProgress(progress)
            onProgress(progress.copy(doneBytes = 50, totalBytes = 100))
            return finish.await()
        }
    }

    @Test
    fun `a download shows how far it is until it ends`() = runTest(UnconfinedTestDispatcher()) {
        val transfer = shownTransfer()
        val download = FakeDownload("holiday.jpg")

        val result = async { transfer.track { download.run(it) } }
        assertEquals(50, shown.value?.doneBytes)
        assertEquals("holiday.jpg", shown.value?.name)

        download.finish.complete("file")
        assertEquals("file", result.await())
        assertNull(shown.value)
    }

    @Test
    fun `cancelling ends the download, and the caller goes on without a result`() = runTest(UnconfinedTestDispatcher()) {
        val transfer = shownTransfer()
        val download = FakeDownload("holiday.jpg")

        val result = async { transfer.track { download.run(it) } }
        transfer.cancel()

        assertNull(result.await())
        assertNull(shown.value)
    }

    @Test
    fun `the download that reported last shows, and cancelling only stops that one`() = runTest(UnconfinedTestDispatcher()) {
        val transfer = shownTransfer()
        val first = FakeDownload("first.pdf")
        val second = FakeDownload("second.pdf")

        val firstResult = async { transfer.track { first.run(it) } }
        val secondResult = async { transfer.track { second.run(it) } }
        assertEquals("second.pdf", shown.value?.name)

        transfer.cancel()
        assertNull(secondResult.await())

        first.finish.complete("first")
        assertEquals("first", firstResult.await())
        assertNull(shown.value)
    }

    @Test
    fun `a failed download reports its error and no longer shows`() = runTest(UnconfinedTestDispatcher()) {
        val transfer = shownTransfer()
        val download = FakeDownload("holiday.jpg")

        val result = async { runCatching { transfer.track { download.run(it) } } }
        download.finish.completeExceptionally(IOException("offline"))

        assertFailsWith<IOException> { result.await().getOrThrow() }
        assertNull(shown.value)
    }
}
