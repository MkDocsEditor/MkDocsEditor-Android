package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.entity.MyObjectBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Files

class WikiDatabaseTest : PersistenceTestBase() {

    @Test
    fun `a switch keeps the tree of each server`() {
        val directory = Files.createTempDirectory("objectbox-test-other").toFile()
        val other = MyObjectBox.builder().directory(directory).build()
        val first = boxStore
        try {
            sections.insertOrUpdateRoot(section("root", documents = listOf("first")))

            database.switchTo(other)
            assertNull(sections.findById("root"))
            sections.insertOrUpdateRoot(section("root", documents = listOf("second")))
            assertEquals(listOf("second"), documents.standardOperation().all.map { it.id })

            database.switchTo(first)
            assertNotNull(sections.findById("root"))
            assertEquals(listOf("first"), documents.standardOperation().all.map { it.id })
        } finally {
            database.switchTo(first)
            other.close()
            directory.deleteRecursively()
        }
    }
}
