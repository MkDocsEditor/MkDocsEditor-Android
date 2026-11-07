package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendServerConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.UserPasswordAuthConfigEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BackendConfigPersistenceManagerTest : PersistenceTestBase() {

    private lateinit var auths: BackendAuthConfigPersistenceManager
    private lateinit var configs: BackendConfigPersistenceManager

    @BeforeEach
    fun createManagers() {
        auths = BackendAuthConfigPersistenceManager(boxStore)
        configs = BackendConfigPersistenceManager(
            boxStore,
            auths,
            BackendServerConfigPersistenceManager(boxStore),
            MkdDocsWebConfigPersistenceManager(boxStore),
        )
    }

    @Test
    fun `stored passwords are removed and the backend login is reported`() {
        val id = configs.add(BackendConfigEntity(name = "wiki").apply {
            serverConfig.target = BackendServerConfigEntity(domain = "backend.example.com", port = 443, useSsl = true)
            authConfig.target = UserPasswordAuthConfigEntity(username = "anna", password = "backend-password")
        })
        // the login of the published wiki, whose relation older versions had: after the upgrade nothing points to it
        auths.standardOperation().put(UserPasswordAuthConfigEntity(username = "reader", password = "wiki-password"))

        val removed = mutableListOf<Pair<Long, String>>()
        configs.removeStoredPasswords { backendId, username -> removed += backendId to username }

        assertEquals(listOf(id to "anna"), removed)
        assertTrue(auths.standardOperation().all.isEmpty())
        val config = configs.standardOperation().get(id)
        assertNull(config.authConfig.target)
        assertEquals("backend.example.com", config.serverConfig.target.domain)
    }
}
