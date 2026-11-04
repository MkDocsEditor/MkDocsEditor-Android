package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BackendConfigPersistenceManagerTest : PersistenceTestBase() {

    private lateinit var configs: BackendConfigPersistenceManager
    private var first = 0L
    private var second = 0L

    @BeforeEach
    fun createConfigs() {
        configs = BackendConfigPersistenceManager(
            boxStore,
            BackendAuthConfigPersistenceManager(boxStore),
            BackendServerConfigPersistenceManager(boxStore),
            MkdDocsWebConfigPersistenceManager(boxStore),
            sections,
        )
        first = configs.standardOperation().put(BackendConfigEntity())
        second = configs.standardOperation().put(BackendConfigEntity())
        configs.selectBackendConfig(first)
        sections.insertOrUpdateRoot(section("root", documents = listOf("d1")))
    }

    @Test
    fun `selecting another server drops the stored tree`() {
        configs.selectBackendConfig(second)

        assertEquals(0, sections.standardOperation().count())
        assertEquals(0, documents.standardOperation().count())
    }

    @Test
    fun `selecting the same server again keeps the stored tree`() {
        configs.selectBackendConfig(first)

        assertEquals(1, sections.standardOperation().count())
    }

    @Test
    fun `removing the selected server drops the stored tree`() {
        assertTrue(configs.remove(first))

        assertEquals(0, sections.standardOperation().count())
    }

    @Test
    fun `removing another server keeps the stored tree`() {
        assertTrue(configs.remove(second))

        assertEquals(1, sections.standardOperation().count())
    }
}
