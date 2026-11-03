package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.MyObjectBox
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import io.objectbox.BoxStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.nio.file.Files

/** A real database in a temporary directory, for each test a new one */
abstract class PersistenceTestBase {

    private lateinit var directory: File
    protected lateinit var boxStore: BoxStore

    protected lateinit var documents: DocumentPersistenceManager
    protected lateinit var contents: DocumentContentPersistenceManager
    protected lateinit var resources: ResourcePersistenceManager
    protected lateinit var sections: SectionPersistenceManager

    @BeforeEach
    fun openDatabase() {
        directory = Files.createTempDirectory("objectbox-test").toFile()
        boxStore = MyObjectBox.builder().directory(directory).build()
        documents = DocumentPersistenceManager(boxStore)
        contents = DocumentContentPersistenceManager(boxStore, documents)
        resources = ResourcePersistenceManager(boxStore)
        sections = SectionPersistenceManager(boxStore, documents, contents, resources)
    }

    @AfterEach
    fun closeDatabase() {
        boxStore.close()
        directory.deleteRecursively()
    }

    /** the way a tree arrives from the server: new entities without a database id */
    protected fun section(
        id: String,
        name: String = id,
        documents: List<String> = emptyList(),
        resources: List<String> = emptyList(),
        subsections: List<SectionEntity> = emptyList(),
    ): SectionEntity {
        val section = SectionEntity(0, id, name)
        subsections.forEach {
            it.parentSection.target = section
            section.subsections.add(it)
        }
        documents.forEach { documentId ->
            val document = DocumentEntity(id = documentId, name = documentId)
            document.parentSection.target = section
            section.documents.add(document)
        }
        resources.forEach { resourceId ->
            val resource = ResourceEntity(id = resourceId, name = resourceId)
            resource.parentSection.target = section
            section.resources.add(resource)
        }
        return section
    }

    protected fun document(id: String): DocumentEntity = documents.standardOperation().all.single { it.id == id }
}
