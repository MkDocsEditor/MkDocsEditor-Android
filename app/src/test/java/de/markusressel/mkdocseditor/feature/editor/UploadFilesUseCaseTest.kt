package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.R
import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.domain.UploadFilesUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.UploadSource
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class UploadFilesUseCaseTest {

    private val images = sectionData("images", resources = listOf(resourceData("r", "a.png")))
    private val page = documentData("page")
    private val root = sectionData("root", subsections = listOf(images), documents = listOf(page))
    private val tree = WikiTree(root)

    private val uploaded = mutableListOf<String>()
    private val cleanedUp = mutableListOf<Uri>()
    private var failOn: String? = null
    private var cancelOn: String? = null

    private val source = object : UploadSource {
        override fun read(uri: Uri) = uri.lastPathSegment!! to ByteArray(4)

        override fun onUploaded(uri: Uri) {
            cleanedUp += uri
        }
    }

    private val useCase = UploadFilesUseCase(
        upload = { sectionId, name, content, onProgress ->
            if (name == failOn) throw IOException("disk full")
            if (name == cancelOn) throw CancellationException("cancelled")
            onProgress(content.size / 2L, content.size.toLong())
            onProgress(content.size.toLong(), content.size.toLong())
            uploaded += "$sectionId/$name"
        },
        source = source,
    )

    private fun uris(vararg names: String) = names.map { Uri.parse("content://files/$it") }

    @Test
    fun `an image becomes an image link relative to the page, with the alt text`() = runTest {
        val result = useCase(tree, page, images, uris("b.png"), altText = " My photo ")
        assertEquals(listOf("images/b.png"), uploaded)
        assertEquals(listOf("![My photo](images/b.png)"), result.snippets)
        assertNull(result.failure)
    }

    @Test
    fun `other files become plain links named after the file`() = runTest {
        assertEquals(listOf("[c.pdf](images/c.pdf)"), useCase(tree, page, images, uris("c.pdf"), "").snippets)
    }

    @Test
    fun `names stay unique within the section and within the upload`() = runTest {
        useCase(tree, page, images, uris("a.png", "a.png"), "")
        assertEquals(3, (uploaded + "images/a.png").toSet().size)
    }

    @Test
    fun `the alt text only applies to a single file`() = runTest {
        val result = useCase(tree, page, images, uris("b.png", "c.png"), "photo")
        assertEquals(listOf("![b](images/b.png)", "![c](images/c.png)"), result.snippets)
    }

    @Test
    fun `a failure keeps the files before it, and the failed file is not cleaned up`() = runTest {
        failOn = "c.png"
        val uris = uris("b.png", "c.png", "d.png")
        val result = useCase(tree, page, images, uris, "")
        assertEquals(listOf("![b](images/b.png)"), result.snippets)
        assertEquals(R.string.unknown_error, result.failure)
        assertEquals(listOf(uris[0]), cleanedUp)
    }

    @Test
    fun `each file reports how far its upload is`() = runTest {
        val reported = mutableListOf<TransferProgress>()
        useCase(tree, page, images, uris("b.png", "c.pdf"), "", onProgress = { reported += it })

        assertEquals(listOf("b.png", "c.pdf"), reported.map { it.name }.distinct())
        assertEquals(listOf(0L, 2L, 4L), reported.filter { it.name == "c.pdf" }.map { it.doneBytes })
        assertEquals(1f, reported.last().fraction)
        assertTrue(reported.all { it.upload })
    }

    @Test
    fun `a cancelled upload is no failure, it ends the upload`() = runTest {
        cancelOn = "c.png"
        assertFailsWith<CancellationException> {
            useCase(tree, page, images, uris("b.png", "c.png"), "")
        }
        assertEquals(listOf("images/b.png"), uploaded)
    }
}
