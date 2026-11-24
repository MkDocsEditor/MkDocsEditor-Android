package de.markusressel.mkdocseditor.application.log

import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorLogTreeTest {

    private val directory: File = createTempDirectory("errors").toFile()
    private val now = { Instant.parse("2026-11-24T11:17:39Z") }
    private val tree = ErrorLogTree(directory, now = now)
    private val log get() = File(directory, ErrorLogTree.FILE_NAME).readText()

    @AfterTest
    fun cleanup() {
        directory.deleteRecursively()
    }

    @Test
    fun `an error keeps its kind and its stack, but not its message`() {
        tree.write(IllegalStateException("text of the wiki page", IllegalArgumentException("alice@example.org")), fatal = false)

        assertContains(log, "2026-11-24T11:17:39Z error\njava.lang.IllegalStateException\n\tat ")
        assertContains(log, "Caused by: java.lang.IllegalArgumentException\n")
        assertContains(log, "ErrorLogTreeTest")
        assertFalse("wiki" in log || "alice" in log)
    }

    @Test
    fun `a panic of Automerge keeps its message`() {
        tree.write(RuntimeException("Rust panic: non-string panic payload"), fatal = true)

        assertContains(log, "crash\njava.lang.RuntimeException: Rust panic: non-string panic payload\n")
    }

    @Test
    fun `the log stays small, with the entries before it in a second file`() {
        val small = ErrorLogTree(directory, maxBytes = 4096, now = now)
        repeat(200) { small.write(IllegalStateException(), fatal = false) }

        val old = File(directory, ErrorLogTree.OLD_FILE_NAME)
        assertTrue(old.exists())
        assertTrue(File(directory, ErrorLogTree.FILE_NAME).length() <= 4096)
        assertTrue(old.length() <= 4096)
        assertTrue(directory.listFiles()!!.size == 2)
    }
}
