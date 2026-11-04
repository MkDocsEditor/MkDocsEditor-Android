package de.markusressel.mkdocseditor.feature.backendconfig

import de.markusressel.mkdocseditor.feature.backendconfig.common.data.ScopeDirectories
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.ScopeTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ScopeDirectoriesTest {

    private val values = mutableMapOf<String, String>()
    private val directories = ScopeDirectories(object : ScopeTable {
        override fun get(key: String) = values[key]
        override fun set(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun keys() = values.keys.toSet()
    })

    @Test
    fun `the first server and account keep the existing data`() {
        assertEquals(ScopeDirectories.EXISTING, directories.directoryOf(1, "markus"))
    }

    @Test
    fun `each server has its own data, and going back finds it again`() {
        val first = directories.directoryOf(1, "markus")
        val second = directories.directoryOf(2, "markus")

        assertNotEquals(first, second)
        assertEquals(first, directories.directoryOf(1, "markus"))
        assertEquals(second, directories.directoryOf(2, "markus"))
    }

    @Test
    fun `each account of a server has its own data`() {
        assertNotEquals(directories.directoryOf(1, "markus"), directories.directoryOf(1, "anna"))
    }

    @Test
    fun `signing out keeps the data of the last account`() {
        directories.directoryOf(1, "markus")
        val anna = directories.directoryOf(1, "anna")

        assertEquals(anna, directories.directoryOf(1, null))
    }

    @Test
    fun `the first sign-in keeps what was stored before it`() {
        val before = directories.directoryOf(1, null)

        assertEquals(before, directories.directoryOf(1, "markus"))
    }

    @Test
    fun `a removed server gives up its directories and doesn't get them back`() {
        val markus = directories.directoryOf(1, "markus")
        val anna = directories.directoryOf(1, "anna")

        assertEquals(setOf(markus, anna), directories.forget(1).toSet())
        assertNotEquals(markus, directories.directoryOf(2, "markus"))
    }

    @Test
    fun `all directories in use are known, and a removed server's are not`() {
        val first = directories.directoryOf(1, "markus")
        val second = directories.directoryOf(12, "markus")
        assertEquals(listOf(first), directories.directoriesOf(1))
        assertEquals(setOf(first, second), directories.all())

        directories.forget(1)

        assertEquals(setOf(second), directories.all())
    }
}
